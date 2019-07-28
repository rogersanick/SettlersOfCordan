package com.contractsAndStates.contracts

import com.contractsAndStates.states.*
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.internal.sumByLong
import net.corda.core.internal.toMultiMap
import net.corda.core.transactions.LedgerTransaction

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
        val inputBoards = tx.inputsOfType<GameBoardState>()
        val outputBoards = tx.outputsOfType<GameBoardState>()
        val inputSettlements = tx.inputsOfType<SettlementState>()
        val outputSettlements = tx.outputsOfType<SettlementState>()
        val outputRoads = tx.outputsOfType<RoadState>()
        val inputResources = tx.inputsOfType<FungibleToken>()
        val outputResources = tx.outputsOfType<FungibleToken>()

        /**
         *  ******** SHAPE ********
         */
        requireThat {
            "There should only be one input of type GameBoardState." using (inputBoards.size == 1)
            "There should be one output state of type GameBoardState" using (outputBoards.size == 1)
        }

        val inputBoard = inputBoards.single()
        val outputBoard = outputBoards.single()

        when (command.value) {

            is Commands.BuildInitialSettlementAndRoad -> requireThat {

                val turnTrackers = tx.referenceInputsOfType<TurnTrackerState>()

                /**
                 *  ******** SHAPE ********
                 *  Check for appropriate 'Shape'. This includes ensuring the appropriate number and type of inputs
                 *  and outputs. In this case the proposing party should include only one input state - the game board
                 *  itself - and between two and five output states, depending on the round of setup we are in.
                 */
                "There should be one output state of type SettlementState" using (outputSettlements.size == 1)
                "There should be one output state of type RoadState" using (outputRoads.size == 1)
                "There should be no resource input states" using (inputResources.isEmpty())
                "There should be one ref input state of type TurnTrackerState" using (turnTrackers.size == 1)

                /**
                 *  ******** BUSINESS LOGIC ********
                 *  Check that the party is proposing a move that is allowed by the rules of the game.
                 */
                val newSettlement = outputSettlements.single()
                val onCorner = newSettlement.absoluteCorner
                val equivalentCorners = inputBoard
                        .getOverlappedCorners(onCorner)
                        .filterNotNull()
                        .plus(onCorner)
                val newRoad = outputRoads.single()
                val turnTracker = turnTrackers.single()
                val currentPlayer = inputBoard.players[turnTracker.currTurnIndex]

                "The road must belong to the board" using(inputBoard.linearId == newRoad.gameBoardPointer.pointer)

                /**
                 * Check Settlements - no previous settlement should be on the place, on the neighboring corners,
                 * and for the overlapping ones.
                 */
                val itAndNeighboringCorners = inputBoard.getItAndNeighboringCorners(onCorner)
                "A settlement must not have previously been built in this vicinity." using
                        itAndNeighboringCorners.none { inputBoard.hasSettlementOn(it) }
                "A settlement cannot be built on a hexTile that is of type Desert" using
                        itAndNeighboringCorners.none {
                            inputBoard.get(it.tileIndex).resourceType == HexTileType.Desert
                        }

                /**
                 * Check Issued Resources - If we are in the first round of setup, the player should not be issuing themselves any resources.
                 * If we are in the second round of setup, the player should be issuing themselves between 1 and 3 resources.
                 */
                if (turnTracker.setUpRound1Complete) {
                    // The list of resources that should be issued in this transaction.
                    val consolidatedResources = equivalentCorners
                            .mapNotNull { corner ->
                                inputBoard.get(corner.tileIndex).resourceType.let { tileType ->
                                    if (tileType != HexTileType.Desert) {
                                        Pair(
                                                tileType.resourceYielded!!,
                                                newSettlement.resourceAmountClaim.toLong())
                                    } else null
                                }
                            }
                            .toMultiMap()
                            .mapValues { it.value.sum() }

                    val tokenAmountsToBeIssued = consolidatedResources.map {
                        it.value of it.key issuedBy currentPlayer heldBy currentPlayer
                    }

                    "The player should be issuing itself resources of the appropriate amount and type" using
                            (outputResources.containsAll(tokenAmountsToBeIssued))
                    "The player should not be issuing itself any additional, undeserved resources" using
                            (outputResources.size == tokenAmountsToBeIssued.size)
                } else {
                    "The player should not be issuing itself any resources in first round of placement" using
                            outputResources.isEmpty()
                }

                "A road must not have previously been built in this location." using
                        inputBoard.getItAndOppositeSides(newRoad.absoluteSide).none {
                            inputBoard.hasRoadOn(it)
                        }

                outputBoard.getItAndOppositeSides(newRoad.absoluteSide).also { sides ->
                    "The new road must have been built in this location." using sides.all {
                        outputBoard.hasRoadOn(it)
                    }
                    "The new road should be adjacent to the proposed settlement" using sides.all { side ->
                        side.getAdjacentCorners().any { equivalentCorners.contains(it) }
                    }
                }

                /**
                 *  ******** Check Signatures ********
                 *  We need to ensure that all parties are signing transactions with the command - BuildInitialSettlementAndRoad.
                 *  Given that we are attempting to maintain a shared fact (that state of the game board) amongst mutually distrusting
                 *  parties, we will often check that all players have signed and verified a transaction.
                 */
                val signingParties = command.signers.toSet()
                val participants = outputBoard.participants.map { it.owningKey }
                "All players must verify and sign the transaction to build an initial settlement and road." using
                        (signingParties.containsAll(participants) && signingParties.size == 4)
            }

            is Commands.BuildSettlement -> requireThat {

                /**
                 *  ******** SHAPE ********
                 */
                "There must be no input settlements" using (inputSettlements.isEmpty())
                "There should be one output state of type SettlementState" using (outputSettlements.size == 1)
                "There should be 4 resource input states" using (inputResources.size == 4)

                /**
                 *  ******** BUSINESS LOGIC ********
                 *  Check that the counter party is proposing a move that is allowed by the rules of the game.
                 */
                val newSettlement = outputSettlements.single()
                val onCorner = newSettlement.absoluteCorner

                val itAndNeighboringCorners = inputBoard.getItAndNeighboringCorners(onCorner)
                "A settlement must not have previously been built in this vicinity." using
                        itAndNeighboringCorners.none { inputBoard.hasSettlementOn(it) }
                "A settlement cannot be built on a hexTile that is of type Desert" using
                        itAndNeighboringCorners.none {
                            inputBoard.get(it.tileIndex).resourceType == HexTileType.Desert
                        }

                verifyPaymentIsEnough(getBuildableCosts(Buildable.Settlement), outputResources, "settlement")

                /**
                 *  ******** SIGNATURES ********
                 *  Check that the necessary parties have signed the transaction.
                 */
                val signingParties = command.signers.toSet()
                val participants = outputBoard.participants.map { it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using
                        (signingParties.containsAll(participants) && signingParties.size == 4)
            }

            is Commands.BuildRoad -> requireThat {

                /**
                 *  ******** SHAPE ********
                 */
                "There should be 1 road state" using (outputRoads.size == 1)
                "There should be 2 resource input states" using (inputResources.size == 2)

                /**
                 *  ******** BUSINESS LOGIC ********
                 *  Check that the counter party is proposing a move that is allowed by the rules of the game.
                 */
                val newRoad = outputRoads.single()

                verifyPaymentIsEnough(getBuildableCosts(Buildable.Road), outputResources, "road")

                "The road must belong to the board" using(inputBoard.linearId == newRoad.gameBoardPointer.pointer)
                "A road must not have previously been built in this location." using
                        inputBoard.getItAndOppositeSides(newRoad.absoluteSide).none {
                            inputBoard.hasRoadOn(it)
                        }
                outputBoard.getItAndOppositeSides(newRoad.absoluteSide).also { sides ->
                    "The new road must have been built in this location." using sides.all {
                        outputBoard.hasRoadOn(it)
                    }
//                    TODO "The new road should be adjacent to a settlement" using sides.all { side ->
//                        side.getAdjacentCorners().any { it == onCorner }
//                    }
                }

                /**
                 *  ******** SIGNATURES ********
                 *  Check that the necessary parties have signed the transaction.
                 */
                val signingParties = command.signers.toSet()
                val participants = outputBoard.participants.map { it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using
                        (signingParties.containsAll(participants) && signingParties.size == 4)
            }

            is Commands.BuildCity -> requireThat {

                /**
                 *  ******** SHAPE ********
                 */
                "There should be 1 input state of type SettlementState" using (inputSettlements.size == 1)
                "There should be 1 output state of type SettlementState" using ((outputSettlements.size == 1))
                "There should be 6 resource input states" using (inputResources.size == 6)

                /**
                 *  ******** BUSINESS LOGIC ********
                 *  Check that the counter party is proposing a move that is allowed by the rules of the game.
                 */
                val inputSettlement = inputSettlements.single()
                val newCity = outputSettlements.single()

                "A city cannot be built on a hexTile that is of type Desert" using
                        (inputBoard.get(newCity.absoluteCorner.tileIndex).resourceType == HexTileType.Desert)

                verifyPaymentIsEnough(getBuildableCosts(Buildable.City), outputResources, "city")

                "The city must be built in the same location as the settlement being upgraded." using
                        (inputSettlement.absoluteCorner == newCity.absoluteCorner)

                /**
                 *  ******** SIGNATURES ********
                 *  Check that the necessary parties have signed the transaction.
                 */
                val signingParties = command.signers.toSet()
                val participants = outputBoard.participants.map { it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using
                        (signingParties.containsAll(participants) && signingParties.size == 4)
            }
        }
    }

    fun GameBoardState.getItAndNeighboringCorners(onCorner: AbsoluteCorner) =
            listOf(onCorner.previous(), onCorner, onCorner.next())
                    .flatMap { getOverlappedCorners(it).plus(it) }
                    .filterNotNull()

    fun GameBoardState.getItAndOppositeSides(onSide: AbsoluteSide) =
            listOfNotNull(onSide, getOpposite(onSide))

    fun verifyPaymentIsEnough(buildableCosts: Map<TokenType, Long>, outputResources: List<FungibleToken>, what: String) =
            verifyPaymentIsEnough(
                    buildableCosts,
                    outputResources.extractTokenAmounts(buildableCosts.keys),
                    what)

    fun verifyPaymentIsEnough(required: Map<TokenType, Long>, payment: Map<TokenType, Long>, what: String) =
            requireThat {
                required.forEach { entry ->
                    "The player must provide at least ${entry.value} of ${entry.key} to purchase $what" using
                            payment[entry.key].let { paid ->
                                paid != null && entry.value <= paid
                            }
                }
            }

    fun List<FungibleToken>.extractTokenAmounts(types: Iterable<TokenType>) = types
            .map { type ->
                type to filter { it.amount.token.tokenType == type }
                        .sumByLong { it.amount.quantity }
            }
            .toMultiMap()
            .mapValues { it.value.sum() }

    interface Commands : CommandData {
        class BuildInitialSettlementAndRoad : Commands
        class BuildSettlement : Commands
        class BuildCity : Commands
        class BuildRoad : Commands
    }
}
