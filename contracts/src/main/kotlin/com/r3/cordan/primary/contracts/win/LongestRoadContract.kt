package com.r3.cordan.primary.contracts.win

import com.r3.cordan.primary.states.board.GameBoardState
import com.r3.cordan.primary.states.structure.RoadState
import com.r3.cordan.primary.states.structure.SettlementState
import com.r3.cordan.primary.states.structure.longestRoad
import com.r3.cordan.primary.states.win.LongestRoadState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class LongestRoadContract : Contract {

    companion object {
        const val ID = "com.r3.cordan.primary.contracts.win.LongestRoadContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        // Get all states required for verification
        val inputRoads = tx.inputsOfType<RoadState>()
        val inputSettlements = tx.inputsOfType<SettlementState>()
        val outputLongestRoadState = tx.outputsOfType<LongestRoadState>().first()

        when (command.value) {
            is Commands.Init -> requireThat {
                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                "Longest Road cannot be assigned to any player at the beginning of the game" using (
                        outputLongestRoadState.holder == null)

                /**
                 *  ******** SIGNATURES ********
                 */

                val signingParties = tx.commandsOfType<Commands.Init>().single().signers.toSet()
                val participants = outputLongestRoadState.participants.map{ it.owningKey }

                "All players must verify and sign the transaction to build a settlement." using(
                        signingParties.containsAll<PublicKey>(participants) && signingParties.size == 4)
            }

            is Commands.Claim -> requireThat {

                // Get the referenced gameboard
                val inputBoard = tx.referenceInputsOfType<GameBoardState>().single()

                val inputLongestRoads = tx.inputsOfType<LongestRoadState>().first()

                /**
                 *  ******** BUSINESS LOGIC ********
                 */
                "All roads must belong to the board" using
                        inputRoads.all { inputBoard.linearId == it.gameBoardLinearId }
                "All settlements must belong to the board" using
                        inputSettlements.all { inputBoard.linearId == it.gameBoardLinearId }

                val candidate = longestRoad(
                        inputBoard.hexTiles, inputRoads, inputSettlements,
                        inputBoard.players, inputLongestRoads.holder)

                "Incorrect longest road holder." using (candidate == outputLongestRoadState.holder)

                /**
                 *  ******** SIGNATURES ********
                 */

                val signingParties = tx.commandsOfType<Commands.Claim>().single().signers.toSet()
                val participants = outputLongestRoadState.participants.map { it.owningKey }

                "All players must verify and sign the transaction to build a settlement." using (
                        signingParties.containsAll(participants) && signingParties.size == 4)
            }
        }
    }

    interface Commands : CommandData {
        class Init: Commands
        class Claim: Commands
    }
}