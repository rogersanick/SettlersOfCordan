package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.BuildPhaseContract
import com.contractsAndStates.states.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.lang.IllegalArgumentException

// *************************
// * Build Settlement Flow *
// *************************

/**
 * This is the flow nodes may execute to consume resources (in addition to an existing settlement and
 * issue a new city state onto the ledger. Cities will be used in the future to claim additional
 * resources in the GatherResourcesFlow.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class BuildCityFlow(val gameBoardLinearId: UniqueIdentifier, val inputSettlementLinearId: UniqueIdentifier, val hexTileIndex: Int, val hexTileCoordinate: Int): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Get a reference to the notary service on the network
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Retrieve the Game Board State from the vault.
        val queryCriteriaForGameBoardState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardLinearId))
        val gameBoardStateAndRef = serviceHub.vaultService.queryBy<GameBoardState>(queryCriteriaForGameBoardState).states.single()
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 3. Retrieve the Turn Tracker State from the vault
        val queryCriteriaForTurnTrackerState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardState.turnTrackerLinearId))
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(serviceHub.vaultService.queryBy<TurnTrackerState>(queryCriteriaForTurnTrackerState).states.single())

        // Step 4. Create a new transaction builder
        val tb = TransactionBuilder(notary)

        // Step 5. Create new commands for placing a settlement and ending a turn. Add both to the transaction.
        val buildCity = Command(BuildPhaseContract.Commands.BuildCity(), gameBoardState.players.map { it.owningKey })
        tb.addCommand(buildCity)

        // Step 6. Create city settlement
        val queryCriteriaForSettlementState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(inputSettlementLinearId))
        val inputSettlementStateAndRef = serviceHub.vaultService.queryBy<SettlementState>(queryCriteriaForSettlementState).states.single()
        val outputCityState = inputSettlementStateAndRef.state.data.upgradeToCity()

        // Step 7. Add the appropriate resources to the transaction to pay for the City.
        generateInGameSpend(serviceHub, tb, getBuildableCosts(Buildable.City), ourIdentity)

        // Step 8. Add all states and commands to the transaction.
        tb.addInputState(inputSettlementStateAndRef)
        tb.addOutputState(outputCityState, BuildPhaseContract.ID)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)

        // Step 9. Sign initial transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 10. Collect all signatures
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // Step 11. Run the FinalityFlow
        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(BuildCityFlow::class)
class BuildCityFlowResponder(val counterpartySession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val gameBoardState = stx.coreTransaction.outputsOfType<GameBoardState>().first()
                val turnTrackerStateRef = stx.coreTransaction.references.single()
                val turnTrackerState = serviceHub.vaultService.queryBy<TurnTrackerState>(QueryCriteria.VaultQueryCriteria(stateRefs = listOf(turnTrackerStateRef))).states.single().state.data

                val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(turnTrackerState.linearId))
                val lastTurnTrackerOnRecordStateAndRef = serviceHub.vaultService.queryBy<TurnTrackerState>(queryCriteria).states.first().state.data

                if (counterpartySession.counterparty.owningKey != gameBoardState.players[lastTurnTrackerOnRecordStateAndRef.currTurnIndex].owningKey) {
                    throw IllegalArgumentException("Only the current player may propose the next move.")
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}
