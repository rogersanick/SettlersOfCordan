package com.contractsAndStates.contracts

import com.contractsAndStates.states.*
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class LongestRoadContract : Contract {

    companion object {
        const val ID = "com.contractsAndStates.contracts.LongestRoadContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        val inputLongestRoads = tx.inputsOfType<LongestRoadState>().first()
        val inputRoads = tx.inputsOfType<RoadState>()
        val inputSettlements = tx.inputsOfType<SettlementState>()
        val inputBoard = tx.inputsOfType<GameBoardState>().single()

        val outputLongestRoadState = tx.outputsOfType<LongestRoadState>().first()

        when (command.value) {
            is Commands.Claim -> requireThat {

                /**
                 *  ******** BUSINESS LOGIC ********
                 */
                "All roads must belong to the board" using
                        inputRoads.all { inputBoard.linearId == it.gameBoardPointer.pointer }

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
        class Claim : Commands
    }
}