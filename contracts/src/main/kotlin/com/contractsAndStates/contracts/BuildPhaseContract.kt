package com.contractsAndStates.contracts

import com.contractsAndStates.states.*
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.utilities.heldBy
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
        val turnTrackerState = tx.inputsOfType<TurnTrackerState>().single()
        val gameBoardState = tx.inputsOfType<GameBoardState>().single()
        val newSettlement = tx.outputsOfType<SettlementState>().single()
        val outputResources = tx.outputsOfType<FungibleToken<Resource>>()
        val hexTileCoordinate = newSettlement.hexTileCoordinate
        val hexTileIndex = newSettlement.hexTileIndex

        // Initialize storage for a list of relevant HexTiles that lay adjacent to the square in question
        val relevantHexTileNeighbours: ArrayList<HexTile?> = arrayListOf()

        if (gameBoardState.hexTiles[hexTileIndex].sides[if (hexTileCoordinate - 1 < 0) 5 else hexTileCoordinate - 1] != null) relevantHexTileNeighbours.add(gameBoardState.hexTiles[gameBoardState.hexTiles[hexTileIndex].sides[if (hexTileCoordinate - 1 < 0) 5 else hexTileCoordinate - 1]!!])
        if (gameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate] != null) relevantHexTileNeighbours.add(gameBoardState.hexTiles[gameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate]!!])

        val indexOfRelevantHexTileNeighbour1 = gameBoardState.hexTiles.indexOf(relevantHexTileNeighbours.getOrNull(0))
        val indexOfRelevantHexTileNeighbour2 = gameBoardState.hexTiles.indexOf(relevantHexTileNeighbours.getOrNull(1))

        // Initialize storage for a list of resources that we should see issued in this transaction.
        val resourcesThatShouldBeIssuedPreConsolidation = arrayListOf<Pair<String, Long>>()
        resourcesThatShouldBeIssuedPreConsolidation.add(Pair(gameBoardState.hexTiles[hexTileIndex].resourceType, newSettlement.resourceAmountClaim.toLong()))
        if (indexOfRelevantHexTileNeighbour1 != -1 && gameBoardState.hexTiles[indexOfRelevantHexTileNeighbour1].resourceType != "Desert") resourcesThatShouldBeIssuedPreConsolidation.add(Pair(gameBoardState.hexTiles[indexOfRelevantHexTileNeighbour1].resourceType, newSettlement.resourceAmountClaim.toLong()))
        if (indexOfRelevantHexTileNeighbour2 != -1 && gameBoardState.hexTiles[indexOfRelevantHexTileNeighbour2].resourceType != "Desert") resourcesThatShouldBeIssuedPreConsolidation.add(Pair(gameBoardState.hexTiles[indexOfRelevantHexTileNeighbour2].resourceType, newSettlement.resourceAmountClaim.toLong()))

        val consolidatedListOfResourceThatShouldBeIssued = mutableMapOf<String, Long>()
        resourcesThatShouldBeIssuedPreConsolidation.forEach{
            if (consolidatedListOfResourceThatShouldBeIssued.containsKey(it.first)) consolidatedListOfResourceThatShouldBeIssued[it.first] = consolidatedListOfResourceThatShouldBeIssued[it.first]!!.plus(it.second)
            else consolidatedListOfResourceThatShouldBeIssued[it.first] = it.second
        }

        val fungibleTokenAmountsOfResourcesThatShouldBeIssued = consolidatedListOfResourceThatShouldBeIssued.map {
            amount(it.value, Resource.getInstance(it.key)) issuedBy gameBoardState.players[turnTrackerState.currTurnIndex] heldBy gameBoardState.players[turnTrackerState.currTurnIndex]
        }

        when (command.value) {

            is Commands.BuildInitialSettlement -> requireThat {

                val turnTracker = tx.inputsOfType<TurnTrackerState>().single()

                if (gameBoardState.hexTiles[hexTileIndex].resourceType == "Desert") {
                    System.out.println("Hello")
                }
                "A settlement cannot be built on a hexTile that is of type Desert" using (gameBoardState.hexTiles[hexTileIndex].resourceType != "Desert")

                if (turnTracker.setUpRound1Complete) {
                    "The player should be issuing them self a resource of the appropriate type" using (outputResources.containsAll(fungibleTokenAmountsOfResourcesThatShouldBeIssued))
                    "The player should be issuing them self a resource of the appropriate type" using (outputResources.size == fungibleTokenAmountsOfResourcesThatShouldBeIssued.size)
                }

                "A settlement must not have previously been built in this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][hexTileCoordinate] )
                "A settlement must not have previously been built beside this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 0) hexTileCoordinate - 1 else 5] )
                "A settlement must not have previously been built beside this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 5) hexTileCoordinate + 1 else 0] )

            }

            is Commands.BuildSettlement -> requireThat {

                "A settlement cannot be built on a hexTile that is of type Desert" using (gameBoardState.hexTiles[hexTileIndex].resourceType == "Desert")

                val referenceTurnTracker = tx.referenceInputRefsOfType<TurnTrackerState>().single().state.data
                val wheatInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Field") }.sumByLong { it.amount.quantity }
                val brickInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Hill") }.sumByLong { it.amount.quantity }
                val sheepInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Pasture") }.sumByLong { it.amount.quantity }
                val woodInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Forest") }.sumByLong { it.amount.quantity }

                "A settlement must not have previously been built in this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][hexTileCoordinate] )
                "A settlement must not have previously been built beside this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 0) hexTileCoordinate - 1 else 5] )
                "A settlement must not have previously been built beside this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 5) hexTileCoordinate + 1 else 0] )

                "The player must have provided the appropriate amount of wheat to build a settlement" using ( wheatInTx == 1.toLong())
                "The player must have provided the appropriate amount of brick to build a settlement" using ( brickInTx == 1.toLong())
                "The player must have provided the appropriate amount of ore to build a settlement" using ( sheepInTx == 1.toLong())
                "The player must have provided the appropriate amount of  to build a settlement" using ( woodInTx == 1.toLong())
                "There must be no input settlements" using (tx.inputsOfType<SettlementState>().size == 1)
                "The player must be attempting to build a single settlement" using (tx.outputsOfType<SettlementState>().size == 1)
            }
        }

    }

    interface Commands : CommandData {
        class BuildInitialSettlement: Commands
        class BuildSettlement: Commands
        class BuildCity: Commands
        class BuildRoad: Commands
    }

}