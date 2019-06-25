package com.contractsAndStates.contracts

import com.contractsAndStates.states.*
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import net.corda.core.contracts.*
import net.corda.core.internal.sumByLong
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
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

                /**
                 *  ******** SHAPE ********
                 *  Check for appropriate 'Shape'. This includes ensuring the appropriate number and type of inputs and outputs.
                 *  In this case the proposing party should include only one input state - the game board itself - and between two
                 *  and five output states, depending on the round of setup we are in.
                 */

                "There should only be one input of type GameBoardState." using ( tx.inputStates.size == 1 && tx.inputStates.single() is GameBoardState)
                "There should be one output state of type GameBoardState" using ( tx.outputsOfType<GameBoardState>().size == 1 )
                "There should be one output state of type SettlementState" using (( tx.outputsOfType<SettlementState>().size == 1 ))
                "There should be one output state of type RoadState" using (( tx.outputsOfType<RoadState>().size == 1 ))

                /**
                 *  ******** BUSINESS LOGIC ********
                 *  Check that the counter party is proposing a move that is allowed by the rules of the game.
                 */

                val newSettlement = tx.outputsOfType<SettlementState>().single()
                val hexTileCoordinate = newSettlement.hexTileCoordinate
                val hexTileIndex = newSettlement.hexTileIndex
                val turnTracker = tx.referenceInputsOfType<TurnTrackerState>().single()

                /**
                 * Check Settlements - In order to determine whether or not a settlement has been previously placed in a given location,
                 * we first have to determine which hexTiles lay adjacent to the one in question.
                 */

                // Here we Initialize storage for a relevant list of relevant HexTiles.
                val relevantHexTileNeighbours: ArrayList<HexTile?> = arrayListOf()

                // Add neighbouring hexTiles to the storage array if they exist.
                if (inputGameBoardState.hexTiles[hexTileIndex].sides[if (hexTileCoordinate - 1 < 0) 5 else hexTileCoordinate - 1] != null) relevantHexTileNeighbours.add(inputGameBoardState.hexTiles[inputGameBoardState.hexTiles[hexTileIndex].sides[if (hexTileCoordinate - 1 < 0) 5 else hexTileCoordinate - 1]!!])
                if (inputGameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate] != null) relevantHexTileNeighbours.add(inputGameBoardState.hexTiles[inputGameBoardState.hexTiles[hexTileIndex].sides[hexTileCoordinate]!!])

                // Get the index of the neighbouringHexTile
                val indexOfRelevantHexTileNeighbour1 = inputGameBoardState.hexTiles.indexOf(relevantHexTileNeighbours.getOrNull(0))
                val indexOfRelevantHexTileNeighbour2 = inputGameBoardState.hexTiles.indexOf(relevantHexTileNeighbours.getOrNull(1))

                "A settlement must not have previously been built in this location." using ( !inputGameBoardState.settlementsPlaced[newSettlement.hexTileIndex][hexTileCoordinate] )
                "A settlement must not have previously been built beside this location." using ( !inputGameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 0) hexTileCoordinate - 1 else 5] )
                "A settlement must not have previously been built beside this location." using ( !inputGameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 5) hexTileCoordinate + 1 else 0] )
                // TODO: Check for the third potential neighbour of any given settlement.

                // Settlements cannot be build on HexTile that have a terrain type of 'Desert'.
                "A settlement cannot be built on a hexTile that is of type Desert" using (inputGameBoardState.hexTiles[hexTileIndex].resourceType != "Desert")

                /**
                 * Check Issued Resources - If we are in the first round of setup, the player should not be issuing themselves any resources.
                 * If we are in the second round of setup, the player should be issuing themselves between 1 and 3 resources.
                 */

                if (turnTracker.setUpRound1Complete) {
                    // Initialize storage for a list of resources that should be issued in this transaction.
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

                    "The player should be issuing themselves resources of the appropriate amount and type" using (outputResources.containsAll(fungibleTokenAmountsOfResourcesThatShouldBeIssued))
                    "The player should not be issuing themselves any additional, undeserved resources" using (outputResources.size == fungibleTokenAmountsOfResourcesThatShouldBeIssued.size)
                } else {
                    "The player should not be issuing them self any resources as this is the first round of placement" using (outputResources.isEmpty())
                }


                /**
                 * Check Roads - We need to check to ensure that the proposed road is not being built on top of an existing road.
                 * We also need to ensure that the proposed road is being connected to the settlement being built.
                 */

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

                /**
                 *  ******** Check Signatures ********
                 *  We need to ensure that all parties are signing transactions with the command - BuildInitialSettlementAndRoad.
                 *  Given that we are attempting to maintain a shared fact (that state of the game board) amongst mutually distrusting
                 *  parties, we will often check that all players have signed and verified a transaction.
                 */

                val signingParties = tx.commandsOfType<Commands.BuildInitialSettlementAndRoad>().single().signers.toSet()
                val participants = outputGameBoardState.participants.map{ it.owningKey }
                "All players must verify and sign the transaction to build an initial settlement and road." using(signingParties.containsAll<PublicKey>(participants) && signingParties.size == 4)


            }

            is Commands.BuildSettlement -> requireThat {

                /**
                 *  ******** SHAPE ********
                 */

                "There should be five input states, 4 resources and 1 gameboard state" using (tx.inputs.size == 5)
                "There should be one output state of type GameBoardState" using ( tx.outputsOfType<GameBoardState>().size == 1 )
                "There should be one output state of type SettlementState" using (( tx.outputsOfType<SettlementState>().size == 1 ))

                /**
                 *  ******** BUSINESS LOGIC ********
                 *  Check that the counter party is proposing a move that is allowed by the rules of the game.
                 */

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

                /**
                 *  ******** SIGNATURES ********
                 *  Check that the necessary parties have signed the transaction.
                 */

                val signingParties = tx.commandsOfType<Commands.BuildSettlement>().single().signers.toSet()
                val participants = outputGameBoardState.participants.map{ it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using(signingParties.containsAll<PublicKey>(participants) && signingParties.size == 4)

            }

            is Commands.BuildRoad -> requireThat {

                /**
                 *  ******** SHAPE ********
                 */

                "There should be three input states, 2 resources and 1 gameboard state" using (tx.inputs.size == 3)
                "There should be two output states, 1 road and 1 gameboard state" using (tx.outputs.size == 2)

                /**
                 *  ******** BUSINESS LOGIC ********
                 *  Check that the counter party is proposing a move that is allowed by the rules of the game.
                 */

                val brickInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Hill") }.sumByLong { it.amount.quantity }
                val woodInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Forest") }.sumByLong { it.amount.quantity }

                "The player must have provided the appropriate amount of brick to build a settlement" using ( brickInTx == (1 * newRoads.size).toLong())
                "The player must have provided the appropriate amount of wood to build a settlement" using ( woodInTx == (1 * newRoads.size).toLong())
                "A road must not have previously been built in this location." using ( newRoads.all { inputGameBoardState.hexTiles[it.hexTileIndex].roads[it.hexTileSide] == null } )

                /**
                 *  ******** SIGNATURES ********
                 *  Check that the necessary parties have signed the transaction.
                 */

                val signingParties = tx.commandsOfType<Commands.BuildRoad>().single().signers.toSet()
                val participants = outputGameBoardState.participants.map{ it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using(signingParties.containsAll<PublicKey>(participants) && signingParties.size == 4)


            }

            is Commands.BuildCity -> requireThat {

                /**
                 *  ******** SHAPE ********
                 */

                "There should be six input states, 5 resources and 1 gameboard state" using (tx.inputs.size == 6)
                "There should be one output state of type GameBoardState" using ( tx.outputsOfType<GameBoardState>().size == 1 )
                "There should be one output state of type SettlementState" using (( tx.outputsOfType<SettlementState>().size == 1 ))
                "There must be no input settlements" using (tx.inputsOfType<SettlementState>().size == 1)
                "The player must be attempting to build a single city" using (tx.outputsOfType<SettlementState>().size == 1)

                /**
                 *  ******** BUSINESS LOGIC ********
                 *  Check that the counter party is proposing a move that is allowed by the rules of the game.
                 */

                val newCity = tx.outputsOfType<SettlementState>().single()
                val inputSettlement = tx.inputsOfType<SettlementState>().single()
                val hexTileIndex = newCity.hexTileIndex

                "A city cannot be built on a hexTile that is of type Desert" using (inputGameBoardState.hexTiles[hexTileIndex].resourceType == "Desert")

                val wheatInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Field") }.sumByLong { it.amount.quantity }
                val oreInTx = outputResources.filter { it.amount.token.tokenType == Resource.getInstance("Mountain") }.sumByLong { it.amount.quantity }

                "The city must be built in the same location as the settlement being upgraded." using ( inputSettlement.hexTileIndex == newCity.hexTileIndex && inputSettlement.hexTileCoordinate == newCity.hexTileCoordinate )

                "The player must have provided the appropriate amount of wheat to build a settlement" using ( wheatInTx == 1.toLong())
                "The player must have provided the appropriate amount of ore to build a settlement" using ( oreInTx == 1.toLong())

                /**
                 *  ******** SIGNATURES ********
                 *  Check that the necessary parties have signed the transaction.
                 */

                val signingParties = tx.commandsOfType<Commands.BuildCity>().single().signers.toSet()
                val participants = outputGameBoardState.participants.map{ it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using(signingParties.containsAll<PublicKey>(participants) && signingParties.size == 4)
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