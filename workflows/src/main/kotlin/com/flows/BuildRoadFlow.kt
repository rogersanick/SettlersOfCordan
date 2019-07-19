package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.BuildPhaseContract
import com.contractsAndStates.contracts.GameStateContract
import com.contractsAndStates.contracts.LongestRoadContract
import com.contractsAndStates.states.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.lang.IllegalArgumentException

// *******************
// * Build Road Flow *
// *******************

/**
 * This flow allows for the creation of a RoadState and its placement
 * on the game board in a non-conflicting position.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class BuildRoadFlow(val gameBoardLinearId: UniqueIdentifier, val hexTileIndex: Int, val hexTileSide: Int): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        val tileIndex = HexTileIndex(hexTileIndex)
        val sideIndex = TileSideIndex(hexTileSide)
        // Step 1. Get a reference to the notary service on the network
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Retrieve the Game Board State from the vault.
        val queryCriteriaForGameBoardState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardLinearId))
        val gameBoardStateAndRef = serviceHub.vaultService.queryBy<GameBoardState>(queryCriteriaForGameBoardState).states.single()
        val gameBoardReferenceStateAndRef = ReferencedStateAndRef(gameBoardStateAndRef)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step *. Retrieve roads, settlements and current longest road state
        val roadStates = serviceHub.vaultService.queryBy<RoadState>().states.map { it.state.data }
        val settlementStates = serviceHub.vaultService.queryBy<SettlementState>().states.map { it.state.data }
        val longestRoadState = serviceHub.vaultService.queryBy<LongestRoadState>().states.map { it.state.data }.first()

        // Step 3. Retrieve the Turn Tracker State from the vault
        val queryCriteriaForTurnTrackerState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardState.turnTrackerLinearId))
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(serviceHub.vaultService.queryBy<TurnTrackerState>(queryCriteriaForTurnTrackerState).states.single())

        // Step 4. Create a new transaction builder
        val tb = TransactionBuilder(notary)

        // Step 5. Create new commands for placing a settlement and ending a turn. Add both to the transaction.
        val buildRoadCommand = Command(BuildPhaseContract.Commands.BuildRoad(), gameBoardState.players.map { it.owningKey })
        tb.addCommand(buildRoadCommand)

        // Step 6. Create initial road state
        val roadState = RoadState(tileIndex, sideIndex, gameBoardState.players, ourIdentity)

        // Step 7. Determine if the road state is extending an existing road
        val newHexTileSetWithRoad = gameBoardState.hexTiles.toBuilder()
                .buildRoad(tileIndex, sideIndex, roadState.linearId)
                .build()
        val outputGameBoardState = gameBoardState.copy(hexTiles = newHexTileSetWithRoad)

        // Step *. Determine new longest road holder
        val longestRoadHolder = longestRoad(gameBoardState.hexTiles, roadStates, settlementStates, gameBoardState.players, longestRoadState.holder)
        val outputLongestRoadState = LongestRoadState(longestRoadHolder, longestRoadState.participants)

        // Step 8. Add all states and commands to the transaction.
        tb.addReferenceState(gameBoardReferenceStateAndRef)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)
        tb.addOutputState(roadState, BuildPhaseContract.ID)
        tb.addOutputState(outputGameBoardState, GameStateContract.ID)
        if (outputLongestRoadState.holder != longestRoadState.holder)
            tb.addOutputState(outputLongestRoadState, LongestRoadContract.ID)

        // Step 9. Sign initial transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 10. Collect all signatures
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(BuildRoadFlow::class)
class BuildRoadFlowResponder(val counterpartySession: FlowSession): FlowLogic<SignedTransaction>() {
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
                    throw IllegalArgumentException("Only the current player may build a road.")
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}
