package com.contractsAndStates.contracts

import com.contractsAndStates.states.*
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import com.sun.org.apache.xpath.internal.operations.Bool
import net.corda.core.contracts.*
import net.corda.core.internal.sumByLong
import net.corda.core.transactions.LedgerTransaction
import java.util.ArrayList

// ************************
// * Build Phase Contract *
// ************************

class BuildPhaseContract : Contract {
    companion object {
        const val ID = "com.contractsAndStates.contracts.BuildPhaseContract"
    }

    override fun verify(tx: LedgerTransaction) {

        // Get access to all of the pieces of the transaction that will be used to verify the contract.
        val command = tx.commands.requireSingleCommand<Commands>()
        val inputGameBoardState = tx.inputsOfType<GameBoardState>().single()
        val outputGameBoardState = tx.outputsOfType<GameBoardState>().single()
        val newRoads = tx.outputsOfType<RoadState>()
        val outputResources = tx.outputsOfType<FungibleToken<Resource>>()

        when (command.value) {

            is Commands.BuildInitialSettlementAndRoad -> requireThat {

                val newSettlement = tx.outputsOfType<SettlementState>().single()
                val hexTileCoordinate = newSettlement.hexTileCoordinate
                val hexTileIndex = newSettlement.hexTileIndex
                val turnTracker = tx.referenceInputsOfType<TurnTrackerState>().single()


                // Initialize storage for a list of relevant HexTiles that lay adjacent to the square in question
                val relevantHexTileNeighbours: ArrayList<HexTile?> = arrayListOf()

                if (inputGameBoardState.hexTiles[hexTileIndex].sides[if (hexTileCoordinate - 1 < 0) 5 else hexTileCoordinate - 1] != null) relevantHexTileNeighbours.add(inputGameBoardState.hexTiles[inputGameBoardState.hexTiles[hexTileIndex].sides[if (hexTileCoordinate - 1 < 0) 5 else hexTileCoordinate - 1]!!])
                if (inputGameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate] != null) relevantHexTileNeighbours.add(inputGameBoardState.hexTiles[inputGameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate]!!])

                val indexOfRelevantHexTileNeighbour1 = inputGameBoardState.hexTiles.indexOf(relevantHexTileNeighbours.getOrNull(0))
                val indexOfRelevantHexTileNeighbour2 = inputGameBoardState.hexTiles.indexOf(relevantHexTileNeighbours.getOrNull(1))

                "A settlement cannot be built on a hexTile that is of type Desert" using (inputGameBoardState.hexTiles[hexTileIndex].resourceType != "Desert")

                // Initialize storage for a list of resources that we should see issued in this transaction.
                val resourcesThatShouldBeIssuedPreConsolidation = arrayListOf<Pair<String, Long>>()
                resourcesThatShouldBeIssuedPreConsolidation.add(Pair(inputGameBoardState.hexTiles[hexTileIndex].resourceType, newSettlement.resourceAmountClaim.toLong()))
                if (indexOfRelevantHexTileNeighbour1 != -1 && inputGameBoardState.hexTiles[indexOfRelevantHexTileNeighbour1].resourceType != "Desert") resourcesThatShouldBeIssuedPreConsolidation.add(Pair(inputGameBoardState.hexTiles[indexOfRelevantHexTileNeighbour1].resourceType, newSettlement.resourceAmountClaim.toLong()))
                if (indexOfRelevantHexTileNeighbour2 != -1 && inputGameBoardState.hexTiles[indexOfRelevantHexTileNeighbour2].resourceType != "Desert") resourcesThatShouldBeIssuedPreConsolidation.add(Pair(inputGameBoardState.hexTiles[indexOfRelevantHexTileNeighbour2].resourceType, newSettlement.resourceAmountClaim.toLong()))

                val consolidatedListOfResourceThatShouldBeIssued = mutableMapOf<String, Long>()
                resourcesThatShouldBeIssuedPreConsolidation.forEach{
                    if (consolidatedListOfResourceThatShouldBeIssued.containsKey(it.first)) consolidatedListOfResourceThatShouldBeIssued[it.first] = consolidatedListOfResourceThatShouldBeIssued[it.first]!!.plus(it.second)
                    else consolidatedListOfResourceThatShouldBeIssued[it.first] = it.second
                }

                val fungibleTokenAmountsOfResourcesThatShouldBeIssued = consolidatedListOfResourceThatShouldBeIssued.map {
                    amount(it.value, Resource.getInstance(it.key)) issuedBy inputGameBoardState.players[turnTracker.currTurnIndex] heldBy inputGameBoardState.players[turnTracker.currTurnIndex]
                }

                if (turnTracker.setUpRound1Complete) {
                    "The player should be issuing them self a resource of the appropriate type" using (outputResources.containsAll(fungibleTokenAmountsOfResourcesThatShouldBeIssued))
                    "The player should be issuing them self a resource of the appropriate type" using (outputResources.size == fungibleTokenAmountsOfResourcesThatShouldBeIssued.size)
                } else {
                    "The player should not be issuing them self any resources as this is the first round of placement" using (outputResources.isEmpty())
                }

                "A settlement must not have previously been built in this location." using ( !inputGameBoardState.settlementsPlaced[newSettlement.hexTileIndex][hexTileCoordinate] )
                "A settlement must not have previously been built beside this location." using ( !inputGameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 0) hexTileCoordinate - 1 else 5] )
                "A settlement must not have previously been built beside this location." using ( !inputGameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 5) hexTileCoordinate + 1 else 0] )
                // TODO: Check for the third potential neighbour of any given settlement.

                "The player should be attempting to build one road." using ( newRoads.size == 1 )

                val hexTileOfNewSettlement = outputGameBoardState.hexTiles[newSettlement.hexTileIndex]

                val indexOfHexTileToCheck1 = hexTileOfNewSettlement.sides[newSettlement.hexTileCoordinate]
                val indexOfHexTileToCheck2 = hexTileOfNewSettlement.sides[if (newSettlement.hexTileCoordinate - 1 < 0) 5 else newSettlement.hexTileCoordinate - 1]

                var checkForThirdPotentialConflictingRoad = true

                if (indexOfHexTileToCheck1 != null && indexOfHexTileToCheck2 != null) {
                    val hexTileToCheck1 = inputGameBoardState.hexTiles[indexOfHexTileToCheck1]
                    val hexTileToCheck2 = inputGameBoardState.hexTiles[indexOfHexTileToCheck2]
                    checkForThirdPotentialConflictingRoad = hexTileToCheck1.roads[hexTileToCheck1.sides.indexOf(hexTileToCheck2.hexTileIndex)] == newRoads.single().linearId
                }

                "The new road should be adjacent to the proposed settlement" using (
                        newRoads.single().linearId == hexTileOfNewSettlement.roads[newSettlement.hexTileCoordinate]
                                || newRoads.single().linearId == hexTileOfNewSettlement.roads[if (newSettlement.hexTileCoordinate + 1 > 5) 0 else newSettlement.hexTileIndex + 1]
                                || checkForThirdPotentialConflictingRoad)

                "A road must not have previously been built in this location." using ( newRoads.all { inputGameBoardState.hexTiles[it.hexTileIndex].roads[it.hexTileSide] == null } )

            }

            is Commands.BuildSettlement -> requireThat {

                val newSettlement = tx.outputsOfType<SettlementState>().single()
                val hexTileCoordinate = newSettlement.hexTileCoordinate
                val hexTileIndex = newSettlement.hexTileIndex

                "A settlement cannot be built on a hexTile that is of type Desert" using (inputGameBoardState.hexTiles[hexTileIndex].resourceType == "Desert")

                val wheatInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Field") }.sumByLong { it.amount.quantity }
                val brickInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Hill") }.sumByLong { it.amount.quantity }
                val sheepInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Pasture") }.sumByLong { it.amount.quantity }
                val woodInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Forest") }.sumByLong { it.amount.quantity }

                "A settlement must not have previously been built in this location." using ( !inputGameBoardState.settlementsPlaced[newSettlement.hexTileIndex][hexTileCoordinate] )
                "A settlement must not have previously been built beside this location." using ( !inputGameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 0) hexTileCoordinate - 1 else 5] )
                "A settlement must not have previously been built beside this location." using ( !inputGameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 5) hexTileCoordinate + 1 else 0] )

                "The player must have provided the appropriate amount of wheat to build a settlement" using ( wheatInTx == 1.toLong())
                "The player must have provided the appropriate amount of brick to build a settlement" using ( brickInTx == 1.toLong())
                "The player must have provided the appropriate amount of ore to build a settlement" using ( sheepInTx == 1.toLong())
                "The player must have provided the appropriate amount of wood to build a settlement" using ( woodInTx == 1.toLong())
                "There must be no input settlements" using (tx.inputsOfType<SettlementState>().size == 1)
                "The player must be attempting to build a single settlement" using (tx.outputsOfType<SettlementState>().size == 1)
            }

            is Commands.BuildRoad -> requireThat {

                val brickInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Hill") }.sumByLong { it.amount.quantity }
                val woodInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Forest") }.sumByLong { it.amount.quantity }

                "The player must have provided the appropriate amount of brick to build a settlement" using ( brickInTx == (1 * newRoads.size).toLong())
                "The player must have provided the appropriate amount of wood to build a settlement" using ( woodInTx == (1 * newRoads.size).toLong())
                "A road must not have previously been built in this location." using ( newRoads.all { inputGameBoardState.hexTiles[it.hexTileIndex].roads[it.hexTileSide] != null } )
            }

            is Commands.BuildCity -> requireThat {


            }


        }

    }

    interface Commands : CommandData {
        class BuildInitialSettlementAndRoad: Commands
        class BuildSettlement: Commands
        class BuildCity: Commands
        class BuildRoad: Commands
    }

}