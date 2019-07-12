package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.BuildPhaseContract
import com.contractsAndStates.contracts.GameStateContract
import com.contractsAndStates.states.*
import com.r3.corda.lib.tokens.contracts.utilities.amount
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.issue.addIssueTokens
import com.r3.corda.lib.tokens.workflows.utilities.addTokenTypeJar
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

// ******************************************
// * Build Initial Settlement And Road Flow *
// ******************************************

/**
 * After setting up the game, this is the first flow that players will run. It allows them
 * to place settlements and roads to establish an initial position on the gameboard. It also
 * facillitates the claiming of initial resources that a player is entitled to when executing
 * the second round of setup (e.g - their second execution of this flow).
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class BuildInitialSettlementAndRoadFlow(val gameBoardLinearId: UniqueIdentifier,
                                        val hexTileIndex: Int,
                                        val hexTileCoordinate: Int,
                                        val hexTileRoadSide: Int
) : FlowLogic<SignedTransaction>() {

    val tileIndex: HexTileIndex
    val cornerIndex: TileCornerIndex
    val sideIndex: TileSideIndex

    init {
        tileIndex = HexTileIndex(hexTileIndex)
        cornerIndex = TileCornerIndex(hexTileCoordinate)
        sideIndex = TileSideIndex(hexTileRoadSide)

        require(cornerIndex.getAdjacentSides().contains(sideIndex)) {
            "You must build a road next to a settlement"
        }
    }

    @Suspendable
    override fun call(): SignedTransaction {

        val tileIndex = HexTileIndex(hexTileIndex)
        val cornerIndex = TileCornerIndex(hexTileCoordinate)
        val sideIndex = TileSideIndex(hexTileRoadSide)
        /**
         * The following section defines all of the steps required to execute the flow. These steps will
         * be executed in sequence to build an initial settlement.
         */

        // Step 1. Get a reference to the notary service on the network
        // TODO We take the notary from the game state
        // val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Retrieve the Game Board State from the vault.
        val queryCriteriaForGameBoardState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardLinearId))
        val gameBoardStateAndRef = serviceHub.vaultService.queryBy<GameBoardState>(queryCriteriaForGameBoardState).states.single()
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 3. Retrieve the Turn Tracker State from the vault
        val queryCriteriaForTurnTrackerState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardState.turnTrackerLinearId))
        val turnTrackerStateAndRef = serviceHub.vaultService.queryBy<TurnTrackerState>(queryCriteriaForTurnTrackerState).states.single()
        val turnTrackerState = turnTrackerStateAndRef.state.data

        // Step 4. Create a new transaction builder
        val tb = TransactionBuilder(gameBoardStateAndRef.state.notary)

        // Step 5. Create new commands for placing a settlement. Add both to the transaction.
        val placeInitialSettlement = Command(BuildPhaseContract.Commands.BuildInitialSettlementAndRoad(), gameBoardState.players.map { it.owningKey })
        tb.addCommand(placeInitialSettlement)

        // Step 6. Create an initial settlement state
        val settlementState = SettlementState(tileIndex, cornerIndex, gameBoardState.players, ourIdentity)

        // Step 7. Create a new Game Board State which will contain an updated mapping of where settlements have been placed.
        val newSettlementsPlaced: MutableList<MutableList<Boolean>> = MutableList(GameBoardState.TILE_COUNT) { i -> MutableList(HexTile.SIDE_COUNT) { j -> gameBoardState.settlementsPlaced[i][j] } }

        // Step 8. Use a linkedList to calculate the coordinates of any relevant overlapping alternative location specification (e.g. 0,2; 1,4 and 5,0 all correspond to a single position)
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

        // Step 9. Use the coordinate calculated for equivalent positions to get the index of each equivalent position.
        // Each hextile has a mapping representing the index of the hexTile adjacent to each of its six sides.
        // The coordinate of the conflicting position allows us to access the appropriate side and get the appropriate index.
        val relevantHexTileNeighbours: ArrayList<HexTile?> = arrayListOf()

        gameBoardState.hexTiles.get(tileIndex).sides.getNeighborsOn(cornerIndex.getAdjacentSides())
                .forEach {
                    if (it != null) relevantHexTileNeighbours.add(gameBoardState.hexTiles.get(it))
                }

        val indexOfRelevantHexTileNeighbour1 = gameBoardState.hexTiles.indexOf(relevantHexTileNeighbours.getOrNull(0))
        val indexOfRelevantHexTileNeighbour2 = gameBoardState.hexTiles.indexOf(relevantHexTileNeighbours.getOrNull(1))

        newSettlementsPlaced[hexTileIndex][hexTileCoordinate] = true
        if (indexOfRelevantHexTileNeighbour1 != null) newSettlementsPlaced[indexOfRelevantHexTileNeighbour1.value][coordinateOfPotentiallyConflictingSettlement1] = true
        if (indexOfRelevantHexTileNeighbour2 != null) newSettlementsPlaced[indexOfRelevantHexTileNeighbour2.value][coordinateOfPotentiallyConflictingSettlement2] = true

        // Step 11. Add self-issued resources if we are in the second round of initial placement
        if (turnTrackerState.setUpRound1Complete) {

            // Calculate the currencies that should be claimed by the player proposing a move.
            val gameCurrencyToClaim = arrayListOf<Pair<HexTileType, Long>>()
            gameCurrencyToClaim.add(Pair(gameBoardState.hexTiles.get(settlementState.hexTileIndex).resourceType, settlementState.resourceAmountClaim.toLong()))
            if (indexOfRelevantHexTileNeighbour1 != null && gameBoardState.hexTiles.get(indexOfRelevantHexTileNeighbour1).resourceType != HexTileType.Desert) gameCurrencyToClaim.add(Pair(gameBoardState.hexTiles.get(indexOfRelevantHexTileNeighbour1).resourceType, settlementState.resourceAmountClaim.toLong()))
            if (indexOfRelevantHexTileNeighbour2 != null && gameBoardState.hexTiles.get(indexOfRelevantHexTileNeighbour2).resourceType != HexTileType.Desert) gameCurrencyToClaim.add(Pair(gameBoardState.hexTiles.get(indexOfRelevantHexTileNeighbour2).resourceType, settlementState.resourceAmountClaim.toLong()))

            // Consolidate the list so that they is only one instance of a given token type issued with the appropriate amount.
            val reducedListOfGameCurrencyToClaim = mutableMapOf<HexTileType, Long>()
            gameCurrencyToClaim.forEach {
                if (reducedListOfGameCurrencyToClaim.containsKey(it.first)) reducedListOfGameCurrencyToClaim[it.first] = reducedListOfGameCurrencyToClaim[it.first]!!.plus(it.second)
                else reducedListOfGameCurrencyToClaim[it.first] = it.second
            }

            // Convert each gameCurrentToClaim into a valid fungible token.
            val fungibleTokenAmountsOfResourcesToClaim = reducedListOfGameCurrencyToClaim.map {
                // TODO make sure the it.key.resourceYielded is not null
                amount(it.value, Resource.getInstance(it.key.resourceYielded!!)) issuedBy ourIdentity heldBy ourIdentity
            }

            addIssueTokens(tb, fungibleTokenAmountsOfResourcesToClaim)
            addTokenTypeJar(fungibleTokenAmountsOfResourcesToClaim, tb)
        }

        // Step 12. Create the road state at the appropriate location specified by the user.
        val roadState = RoadState(tileIndex, sideIndex, gameBoardState.players, ourIdentity, null, null)

        // Step 13. Update the gameBoardState hextiles with the roads being built.
        val newHexTiles = gameBoardState.hexTiles.get(tileIndex).buildRoad(sideIndex, roadState.linearId, PlacedHexTiles(gameBoardState.hexTiles.value))

        // Step 14. Update the gameBoardState with new hexTiles and built settlements.
        val fullyUpdatedOutputGameBoardState = gameBoardState.copy(settlementsPlaced = newSettlementsPlaced, hexTiles = newHexTiles)

        // Step 15. Add all states and commands to the transaction.
        tb.addInputState(gameBoardStateAndRef)
        tb.addReferenceState(ReferencedStateAndRef(turnTrackerStateAndRef))
        tb.addOutputState(settlementState, BuildPhaseContract.ID)
        tb.addOutputState(roadState, BuildPhaseContract.ID)
        tb.addOutputState(fullyUpdatedOutputGameBoardState)
        tb.addCommand(GameStateContract.Commands.UpdateWithSettlement(), gameBoardState.players.map { it.owningKey })

        // Step 16. Sign initial transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 16. Collect all signatures
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // Step 17. Run the FinalityFlow to persist the transaction to the ledgers of all appropriate parties.
        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(BuildInitialSettlementAndRoadFlow::class)
class BuildInitialSettlementFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {

                // Get access to the GameBoardState and TurnTracker from the transaction
                val gameBoardState = stx.coreTransaction.outputsOfType<GameBoardState>().first()
                val turnTrackerStateRef = stx.coreTransaction.references.first()
                val turnTrackerQueryCriteria = QueryCriteria.VaultQueryCriteria(stateRefs = listOf(turnTrackerStateRef))
                val turnTrackerState = serviceHub.vaultService.queryBy<TurnTrackerState>(turnTrackerQueryCriteria).states.first().state.data

                // Build query criteria to retrieve that last evolution of the turn tracker we have on record.
                val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardState.turnTrackerLinearId))
                val lastTurnTrackerOnRecordStateAndRef = serviceHub.vaultService.queryBy<TurnTrackerState>(queryCriteria).states.first().state.data

                // Get all of the settlements currently allocated to this player
                val currentSettlementsBelongingToPlayer = serviceHub.vaultService.queryBy<SettlementState>().states.filter { it.state.data.owner == counterpartySession.counterparty }

                if (!turnTrackerState.setUpRound1Complete && currentSettlementsBelongingToPlayer.isNotEmpty()) {
                    throw IllegalArgumentException("The current player has already built an initial settlement in round 1.")
                } else if (turnTrackerState.setUpRound1Complete && currentSettlementsBelongingToPlayer.size > 1) {
                    throw IllegalArgumentException("The current player has already built an initial settlement in round 2.")
                }

                // Ensure that the player proposing the build of a settlement is currently the player whose turn it is.
                if (counterpartySession.counterparty.owningKey != gameBoardState.players[lastTurnTrackerOnRecordStateAndRef.currTurnIndex].owningKey) {
                    throw IllegalArgumentException("Only the current player may propose the next move.")
                }

                if (turnTrackerState.setUpRound2Complete) {
                    throw IllegalArgumentException("You should be using the end turn function")
                }
            }
        }

        // Sign the transaction
        val txWeJustSignedId = subFlow(signedTransactionFlow)

        // Run the ReceiveFinalityFlow flow to finalize the transaction.
        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}
