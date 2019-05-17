package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.GatherPhaseContract
import com.contractsAndStates.states.*
import com.oracleClient.contracts.DiceRollContract
import com.oracleClient.state.DiceRollState
import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import com.r3.corda.sdk.token.workflow.flows.IssueToken
import com.r3.corda.sdk.token.workflow.flows.RedeemToken
import com.r3.corda.sdk.token.workflow.selection.TokenSelection
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.lang.IllegalArgumentException

/**
 * This flow will allow users to exchange their existing assets at a port at the specified exchange rate. It is
 * currently unimplemented and is a copy / paste of the IssueTokenFlow. The issue here surrounds the burning of
 * the tokens - which is currently impossible. A potential interim solution might be issuing a new identity on the
 * network and having them act as an inactive 'burnt-token-collector'.
 */

@InitiatingFlow
@StartableByRPC
class TradeWithPortFlow(val gameBoardLinearId: UniqueIdentifier, val indexOfPort: Int, val coordinateOfPort: Int, val resourceAmount: Long, val resourceType: String): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Step 1. Get reference to the notary and oracle
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Retrieve the Game Board State from the vault.
        val queryCriteriaForGameBoardState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardLinearId))
        val gameBoardStateAndRef = serviceHub.vaultService.queryBy<GameBoardState>(queryCriteriaForGameBoardState).states.single()
        val gameBoardReferenceStateAndRef = ReferencedStateAndRef(gameBoardStateAndRef)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 3. Retrieve the Turn Tracker State from the vault
        val queryCriteriaForTurnTrackerState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardState.turnTrackerLinearId))
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(serviceHub.vaultService.queryBy<TurnTrackerState>(queryCriteriaForTurnTrackerState).states.single())

        // Step 4. Get access to the port with which the user wishes to trade
        val portToBeTradedWith = gameBoardState.ports.filter { it.accessPoints.any { accessPoint ->  accessPoint.hexTileIndex == indexOfPort && accessPoint.hexTileCoordinate.contains(coordinateOfPort) } }.single().portTile

        // Step 5. Generate an exit for the tokens that will be consumed by the port.
        val resourceTypeParsed = Resource.getInstance(resourceType)
        val amountOfResourceType = Amount(resourceAmount, resourceTypeParsed)
        val tb = TransactionBuilder(notary)
        generateInGameSpend(serviceHub, tb, mapOf(Pair(resourceTypeParsed, amountOfResourceType)), ourIdentity)

        // Step 6. Generate all tokens and commands for issuance from the port
        portToBeTradedWith.outputRequired.forEach {
            tb.addOutputState(it issuedBy ourIdentity heldBy ourIdentity )
            tb.addCommand(IssueTokenCommand(it.token issuedBy ourIdentity))
        }

        // Step 7. Add all necessary states to the transaction
        tb.addReferenceState(gameBoardReferenceStateAndRef)

        // Step 8. Verify Transaction
        tb.verify(serviceHub)

        // Step 9. Collect the signatures and sign the transaction
        val ptx = serviceHub.signInitialTransaction(tb)
        val sessions = (gameBoardState.players - ourIdentity).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))
        return subFlow(FinalityFlow(stx, sessions))
    }

}

@InitiatedBy(TradeWithPortFlow::class)
open class TradeWithPortFlowResponder(val counterpartySession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val lastTurnTrackerOnRecordStateAndRef = serviceHub.vaultService.queryBy<TurnTrackerState>().states.single().state.data
                val gameBoardState = serviceHub.vaultService.queryBy<GameBoardState>().states.single().state.data

                if (counterpartySession.counterparty.owningKey != gameBoardState.players[lastTurnTrackerOnRecordStateAndRef.currTurnIndex].owningKey) {
                    throw IllegalArgumentException("Only the current player may propose the next move.")
                }

            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }

}