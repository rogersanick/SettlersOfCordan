package net.corda.cordan.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.cordan.contract.BuildPhaseContract
import net.corda.cordan.contract.GameStateContract
import net.corda.cordan.contract.TurnTrackerContract
import net.corda.cordan.state.GameBoardState
import net.corda.cordan.state.SettlementState
import net.corda.cordan.state.TurnTrackerState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder


// *************************************
// * Initial Settlement Placement Flow *
// *************************************

@InitiatingFlow(version = 1)
@StartableByRPC
class BuildInitialSettlementFlow(val gameBoardLinearId: UniqueIdentifier, val hexTileIndex: Int, val hexTileCoordinate: Int): FlowLogic<SignedTransaction>() {
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
        val turnTrackerStateAndRef = serviceHub.vaultService.queryBy<TurnTrackerState>(queryCriteriaForTurnTrackerState).states.single()
        val turnTrackerState = turnTrackerStateAndRef.state.data

        // Step 4. Create a new transaction builder
        val tb = TransactionBuilder(notary)

        // Step 5. Create new commands for placing a settlement and ending a turn. Add both to the transaction.
        val endTurnDuringInitialPlacementPhase = Command(TurnTrackerContract.Commands.EndTurnDuringInitialPlacementPhase(), ourIdentity.owningKey)
        val placeInitialSettlement = Command(BuildPhaseContract.Commands.BuildInitialSettlement(), gameBoardState.players.map { it.owningKey })

        tb.addCommand(placeInitialSettlement)
        tb.addCommand(endTurnDuringInitialPlacementPhase)

        // Step 6. Create initial settlement
        val settlementState = SettlementState(hexTileIndex, hexTileCoordinate, gameBoardState.players, ourIdentity)

        // Step 7. Create the new turnTracker state.
        val newTurnTrackerState = turnTrackerState.endTurnDuringInitialSettlementPlacement()

        // Step X. Create a new Game Board State
        val newSettlementsPlaced: MutableList<MutableList<Boolean>> = MutableList(18) { kotlin.collections.MutableList(6) { false } }
        newSettlementsPlaced[hexTileIndex][hexTileCoordinate] = true

        // Step 8. Add all states and commands to the transaction.
        tb.addInputState(gameBoardStateAndRef)
        tb.addInputState(turnTrackerStateAndRef)
        tb.addOutputState(settlementState, BuildPhaseContract.ID)
        tb.addOutputState(newTurnTrackerState, GameStateContract.ID)
        tb.addOutputState(gameBoardState.copy(settlementsPlaced = newSettlementsPlaced))

        // Step 9. Sign initial transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 10. Collect all signatures
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(BuildInitialSettlementFlow::class)
class BuildInitialSettlementFlowResponder(val counterpartySession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                stx.verify(serviceHub, false)
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}
