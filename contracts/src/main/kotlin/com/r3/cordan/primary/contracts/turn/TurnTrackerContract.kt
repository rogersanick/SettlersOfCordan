package com.r3.cordan.primary.contracts.turn

import com.r3.cordan.primary.states.turn.TurnTrackerState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

// *************************
// * Turn Tracker Contract *
// *************************

class TurnTrackerContract : Contract {

    companion object {
        const val ID = "com.r3.cordan.primary.contracts.turn.TurnTrackerContract"
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()

        val turnTrackerInputs = tx.inputsOfType<TurnTrackerState>()
        val turnTrackerOutputs = tx.outputsOfType<TurnTrackerState>()

        requireThat {
            "There must be exactly one Turn Tracker output state." using (turnTrackerOutputs.size == 1)
        }

        val outputTurnTracker = turnTrackerOutputs.single()

        tx.commandsOfType<Commands.EndTurnDuringInitialPlacementPhase>()

        when (command.value) {

            is Commands.EndTurn -> requireThat {
                "There must be exactly one Turn Tracker input state." using (turnTrackerInputs.size == 1)
                val inputTurnTrackerState = turnTrackerInputs.single()
                "Both setup rounds must have been completed" using
                        (outputTurnTracker.setUpRound1Complete && outputTurnTracker.setUpRound2Complete)
                "The turn tracker must be incremented by 1" using
                        (outputTurnTracker.currTurnIndex == inputTurnTrackerState.currTurnIndex + 1 ||
                                (outputTurnTracker.currTurnIndex == 0 && inputTurnTrackerState.currTurnIndex == 3))
            }

            is Commands.EndTurnDuringInitialPlacementPhase -> requireThat {
                "There must be exactly one Turn Tracker input state." using (turnTrackerInputs.size == 1)
                val inputTurnTrackerState = turnTrackerInputs.single()
                if (inputTurnTrackerState.setUpRound1Complete && !outputTurnTracker.setUpRound2Complete) {
                    "The turn tracker must be decremented to reflect the reverse placement order" using
                            (inputTurnTrackerState.currTurnIndex - 1 == outputTurnTracker.currTurnIndex)
                } else if (outputTurnTracker.setUpRound2Complete) {
                    "The turn tracker must reset when both setup rounds are complete" using
                            (outputTurnTracker.currTurnIndex == 0)
                } else if (!outputTurnTracker.setUpRound1Complete) {
                    "The turn tracker must be incremented to reflect the placement order" using
                            (inputTurnTrackerState.currTurnIndex + 1 == outputTurnTracker.currTurnIndex)
                }
            }

            is Commands.CreateTurnTracker -> requireThat {
                "The turn tracker must be initialized at a 0 index." using (outputTurnTracker.currTurnIndex == 0)
            }
        }
    }

    interface Commands : CommandData {
        class EndTurn: Commands
        class EndTurnDuringInitialPlacementPhase: Commands
        class CreateTurnTracker: Commands
    }
}