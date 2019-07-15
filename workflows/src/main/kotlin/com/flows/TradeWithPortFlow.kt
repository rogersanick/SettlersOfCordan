package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.TradePhaseContract
import com.contractsAndStates.states.*
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * This flow will allow users to exchange their existing assets at a port at the specified exchange rate. It is
 * currently unimplemented and is a copy / paste of the IssueTokenFlow. The issue here surrounds the burning of
 * the tokens - which is currently impossible. A potential interim solution might be issuing a new identity on the
 * network and having them act as an inactive 'burnt-token-collector'.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class TradeWithPortFlow(
        val gameBoardLinearId: UniqueIdentifier,
        tileOfPort: Int,
        tileCornerOfPort: Int,
        val soldResource: ResourceType,
        val purchasedResource: ResourceType) : FlowLogic<SignedTransaction>() {

    val hexTileOfPort: HexTileIndex
    val tileCorner: TileCornerIndex

    init {
        hexTileOfPort = HexTileIndex((tileOfPort))
        tileCorner = TileCornerIndex(tileCornerOfPort)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        // Step 1. Get reference to the notary and oracle
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Retrieve the Game Board State from the vault.
        val queryCriteriaForGameBoardState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardLinearId))
        val gameBoardStateAndRef = serviceHub.vaultService.queryBy<GameBoardState>(queryCriteriaForGameBoardState).states.single()
        val gameBoardReferenceStateAndRef = ReferencedStateAndRef(gameBoardStateAndRef)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 3. Get access to the port with which the user wishes to trade
        val portToBeTradedWith = gameBoardState.ports.getPortAt(hexTileOfPort, tileCorner).portTile

        // Step 4. Generate an exit for the tokens that will be consumed by the port.
        val tb = TransactionBuilder(notary)
        val sold = portToBeTradedWith.getInputOf(soldResource)
        generateInGameSpend(serviceHub, tb, mapOf(Pair(sold.token, sold)), ourIdentity)

        // Step 5. Generate all tokens and commands for issuance from the port
        val playerKeys = gameBoardState.players.map { it.owningKey }
        val purchased = portToBeTradedWith.getOutputOf(purchasedResource)
        tb.addOutputState(purchased issuedBy ourIdentity heldBy ourIdentity)
        tb.addCommand(TradePhaseContract.Commands.TradeWithPort(), playerKeys)
        tb.addCommand(IssueTokenCommand(purchased.token issuedBy ourIdentity), playerKeys)

        // Step 6. Add all necessary states to the transaction
        tb.addReferenceState(gameBoardReferenceStateAndRef)

        // Step 7. Verify Transaction
        tb.verify(serviceHub)

        // Step 8. Collect the signatures and sign the transaction
        val ptx = serviceHub.signInitialTransaction(tb)
        val sessions = (gameBoardState.players - ourIdentity).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))
        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(TradeWithPortFlow::class)
open class TradeWithPortFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val gameBoardStateRef = stx.coreTransaction.references.single()
                val gameBoardStateQueryCriteria = QueryCriteria.VaultQueryCriteria(stateRefs = listOf(gameBoardStateRef))
                val gameBoardState = serviceHub.vaultService
                        .queryBy<GameBoardState>(gameBoardStateQueryCriteria)
                        .states.single().state.data

                val turnTrackerStateLinearId = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardState.turnTrackerLinearId))
                val lastTurnTrackerOnRecordStateAndRef = serviceHub.vaultService
                        .queryBy<TurnTrackerState>(turnTrackerStateLinearId)
                        .states.first().state.data

                if (counterpartySession.counterparty.owningKey != gameBoardState.players[lastTurnTrackerOnRecordStateAndRef.currTurnIndex].owningKey) {
                    throw IllegalArgumentException("Only the current player may propose the next move.")
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}