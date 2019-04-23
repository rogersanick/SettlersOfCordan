package com.contractsAndStates.contracts

import com.contractsAndStates.states.TurnTrackerState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

// *************************
// * Turn Tracker Contract *
// *************************

class TurnTrackerContract : Contract {

    companion object {
        const val ID = "com.contractsAndStates.contracts.TurnTrackerContract"
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()

        val listOfTurnTrackerInputStates = tx.inputsOfType<TurnTrackerState>()
        val listOfTurnTrackerOutputStates = tx.outputsOfType<TurnTrackerState>()
        val outputTurnTrackerState = listOfTurnTrackerOutputStates.first()

        tx.commandsOfType<Commands.EndTurnDuringInitialPlacementPhase>()

        when (command.value) {

            is Commands.EndTurn -> requireThat {
                val inputTurnTrackerState = tx.inputsOfType<TurnTrackerState>().firstOrNull() as TurnTrackerState
                "There must be exactly one Turn Tracker input state." using (listOfTurnTrackerInputStates.size == 1)
                "There must be exactly one Turn Tracker output state." using (listOfTurnTrackerOutputStates.size == 1)
                "Both setup rounds must have been completed" using (outputTurnTrackerState.setUpRound1Complete && outputTurnTrackerState.setUpRound2Complete)
                "The turn tracker must be incremented by 1." using (outputTurnTrackerState.currTurnIndex == inputTurnTrackerState.currTurnIndex + 1 || (outputTurnTrackerState.currTurnIndex == 0 && inputTurnTrackerState.currTurnIndex == 3))
            }

            is Commands.EndTurnDuringInitialPlacementPhase -> requireThat {
                val inputTurnTrackerState = tx.inputsOfType<TurnTrackerState>().firstOrNull() as TurnTrackerState
                "There must be exactly one Turn Tracker input state." using (listOfTurnTrackerInputStates.size == 1)
                "There must be exactly one Turn Tracker output state." using (listOfTurnTrackerOutputStates.size == 1)
                if (inputTurnTrackerState.setUpRound1Complete && !outputTurnTrackerState.setUpRound2Complete) {
                    "The turn tracker must be decremented to reflect the reverse placement order" using (inputTurnTrackerState.currTurnIndex - 1 == outputTurnTrackerState.currTurnIndex)
                } else if (outputTurnTrackerState.setUpRound2Complete) {
                    "The turn tracker must reset when both setup rounds are complete" using (outputTurnTrackerState.currTurnIndex == 0)
                } else if (!outputTurnTrackerState.setUpRound1Complete) {
                    "The turn tracker must be incremented to reflect the placement order" using (inputTurnTrackerState.currTurnIndex + 1 == outputTurnTrackerState.currTurnIndex)
                }
            }

            is Commands.CreateTurnTracker -> requireThat {
                "There must be exactly one Turn Tracker output state." using (listOfTurnTrackerOutputStates.size == 1)
                "The turn tracker must be initialized at a 0 index." using (outputTurnTrackerState.currTurnIndex == 0)
            }

        }


    }

    interface Commands : CommandData {
        class EndTurn: Commands
        class EndTurnDuringInitialPlacementPhase: Commands
        class CreateTurnTracker: Commands
    }

}