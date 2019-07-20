package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.BuildPhaseContract
import com.contractsAndStates.contracts.GameStateContract
import com.contractsAndStates.states.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*


// *************************
// * Build Settlement Flow *
// *************************

/**
 * This is the flow nodes may execute to consume resources and issue a new settlement
 * state onto the ledger at a location of their choosing. New settlements will be used
 * in the future to claim additional resources in the GatherResourcesFlow.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class BuildSettlementFlow(
        val gameBoardLinearId: UniqueIdentifier,
        hexTileIndex: Int,
        val hexTileCoordinate: Int
) : FlowLogic<SignedTransaction>() {

    val tileIndex: HexTileIndex
    val cornerIndex: TileCornerIndex

    init {
        tileIndex = HexTileIndex(hexTileIndex)
        cornerIndex = TileCornerIndex(hexTileCoordinate)
    }

    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 2. Get a reference to the notary service on the network
        val notary = gameBoardStateAndRef.state.notary

        // Step 3. Retrieve the Turn Tracker State from the vault
        val turnTrackerStateAndRef = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.linearId)
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(turnTrackerStateAndRef)

        // Step 4. Create a new transaction builder
        val tb = TransactionBuilder(notary)

        // Step 5. Create new commands for placing a settlement and ending a turn. Add both to the transaction.
        val buildSettlement = Command(BuildPhaseContract.Commands.BuildSettlement(), gameBoardState.players.map { it.owningKey })
        tb.addCommand(buildSettlement)

        // Step 6. Create initial settlement
        val settlementState = SettlementState(tileIndex, cornerIndex, gameBoardState.players, ourIdentity)

        // Step 7. Create a new Game Board State
        val newSettlementsPlaced = PlacedSettlements.Builder()

        // Step 8. Calculate the coordinates of neighbours that could conflict with the proposed placement were they to exist.
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

        // Step 9. Calculate the index of potentially conflicting neighbours, should they have been previously built.
        val relevantHexTileNeighbours: ArrayList<HexTile?> = arrayListOf()

        gameBoardState.hexTiles.get(tileIndex).sides.getNeighborsOn(cornerIndex.getAdjacentSides())
                .forEach {
                    if (it != null) relevantHexTileNeighbours.add(gameBoardState.hexTiles.get(it))
                }

        val indexOfRelevantHexTileNeighbour1 = gameBoardState.hexTiles.indexOf(relevantHexTileNeighbours.getOrNull(0))
        val indexOfRelevantHexTileNeighbour2 = gameBoardState.hexTiles.indexOf(relevantHexTileNeighbours.getOrNull(1))

        newSettlementsPlaced.placeOn(tileIndex, cornerIndex)
        if (indexOfRelevantHexTileNeighbour1 != null) newSettlementsPlaced.placeOn(indexOfRelevantHexTileNeighbour1, TileCornerIndex(coordinateOfPotentiallyConflictingSettlement1))
        if (indexOfRelevantHexTileNeighbour2 != null) newSettlementsPlaced.placeOn(indexOfRelevantHexTileNeighbour2, TileCornerIndex(coordinateOfPotentiallyConflictingSettlement2))

        // Step 10. Add the appropriate resources to the transaction to pay for the Settlement.
        generateInGameSpend(serviceHub, tb, getBuildableCosts(Buildable.Settlement), ourIdentity)

        // Step 11. Add all states and commands to the transaction.
        tb.addInputState(gameBoardStateAndRef)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)
        tb.addOutputState(settlementState, BuildPhaseContract.ID)
        tb.addOutputState(gameBoardState.copy(settlementsPlaced = newSettlementsPlaced.build()))
        tb.addCommand(GameStateContract.Commands.UpdateWithSettlement(), gameBoardState.players.map { it.owningKey })

        serviceHub.networkMapCache.notaryIdentities.first()
        serviceHub.networkMapCache.notaryIdentities.first()
        // Step 12. Sign initial transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 13. Collect all signatures
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(BuildSettlementFlow::class)
class BuildSettlementFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val gameBoardState = stx.coreTransaction.outputsOfType<GameBoardState>().first()
                val turnTrackerStateRef = stx.coreTransaction.references.single()
                val turnTrackerState = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(turnTrackerStateRef)
                        .state.data

                val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(turnTrackerState.linearId))
                val lastTurnTrackerOnRecordStateAndRef = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(turnTrackerState.linearId)
                        .state.data

                if (counterpartySession.counterparty.owningKey !=
                        gameBoardState.players[lastTurnTrackerOnRecordStateAndRef.currTurnIndex].owningKey) {
                    throw IllegalArgumentException("Only the current player may propose the next move.")
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}
