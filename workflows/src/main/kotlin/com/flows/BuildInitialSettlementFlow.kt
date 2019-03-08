package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.BuildPhaseContract
import com.contractsAndStates.contracts.GameStateContract
import com.contractsAndStates.contracts.TurnTrackerContract
import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.HexTile
import com.contractsAndStates.states.SettlementState
import com.contractsAndStates.states.TurnTrackerState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.lang.IllegalArgumentException
import java.util.ArrayList

// *********************************
// * Build Initial Settlement Flow *
// *********************************

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

        // Step X. Get access to potentially conflicting neighbours
        class LinkedListNode(val int: Int, var next: LinkedListNode? = null)
        val linkedListNode1 = LinkedListNode(1)
        val linkedListNode2 = LinkedListNode(3)
        linkedListNode1.next = linkedListNode2
        val linkedListNode3 = LinkedListNode(5)
        linkedListNode2.next = linkedListNode3
        linkedListNode3.next = linkedListNode1

        val linkedList2Node1 = LinkedListNode(0)
        val linkedList2Node2 = LinkedListNode(2)
        linkedList2Node1.next = linkedList2Node2
        val linkedList2Node3 = LinkedListNode(4)
        linkedList2Node2.next = linkedList2Node3
        linkedList2Node3.next = linkedList2Node1

        var linkedListToGetCoordinateOfPotentiallyConflictingSettlement: LinkedListNode?

        if (hexTileCoordinate % 2 == 0) {
            linkedListToGetCoordinateOfPotentiallyConflictingSettlement = linkedList2Node1
            while (hexTileCoordinate != linkedListToGetCoordinateOfPotentiallyConflictingSettlement?.int) {
                linkedListToGetCoordinateOfPotentiallyConflictingSettlement = linkedListToGetCoordinateOfPotentiallyConflictingSettlement?.next
            }
        } else {
            linkedListToGetCoordinateOfPotentiallyConflictingSettlement = linkedListNode2
            while (hexTileCoordinate != linkedListToGetCoordinateOfPotentiallyConflictingSettlement?.int) {
                linkedListToGetCoordinateOfPotentiallyConflictingSettlement = linkedListToGetCoordinateOfPotentiallyConflictingSettlement?.next
            }
        }

        val coordinateOfPotentiallyConflictingSettlement1 = linkedListToGetCoordinateOfPotentiallyConflictingSettlement.next?.int!!
        val coordinateOfPotentiallyConflictingSettlement2 = linkedListToGetCoordinateOfPotentiallyConflictingSettlement.next?.next?.int!!

        val relevantHexTileNeighbours: ArrayList<HexTile?> = arrayListOf()

        if (hexTileCoordinate != 5) {
            if (gameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate - 1] != null) relevantHexTileNeighbours.add(gameBoardState.hexTiles[gameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate - 1]!!])
            if (gameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate] != null) relevantHexTileNeighbours.add(gameBoardState.hexTiles[gameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate]!!])
        } else {
            if (gameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate - 1] != null) relevantHexTileNeighbours.add(gameBoardState.hexTiles[gameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate - 1]!!])
            if (gameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate] != null) relevantHexTileNeighbours.add(gameBoardState.hexTiles[gameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate]!!])
        }

        val indexOfRelevantHexTileNeighbour1 = gameBoardState.hexTiles.indexOf(relevantHexTileNeighbours.getOrNull(0))
        val indexOfRelevantHexTileNeighbour2 = gameBoardState.hexTiles.indexOf(relevantHexTileNeighbours.getOrNull(1))

        newSettlementsPlaced[hexTileIndex][hexTileCoordinate] = true
        if (indexOfRelevantHexTileNeighbour1 != -1) newSettlementsPlaced[indexOfRelevantHexTileNeighbour1][coordinateOfPotentiallyConflictingSettlement1] = true
        if (indexOfRelevantHexTileNeighbour2 != -1) newSettlementsPlaced[indexOfRelevantHexTileNeighbour2][coordinateOfPotentiallyConflictingSettlement2] = true

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
                val gameBoardState = stx.coreTransaction.outputsOfType<GameBoardState>().first()
                val turnTrackerState = stx.coreTransaction.outputsOfType<TurnTrackerState>().first()

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
