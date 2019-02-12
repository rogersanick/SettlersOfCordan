package net.corda.cordan.contract

import net.corda.cordan.state.TurnTrackerState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

// *************************
// * Turn Tracker Contract *
// *************************

class TurnTrackerContract : Contract {

    companion object {
        const val ID = "net.corda.cordan.contract.TurnTrackerContract"
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<TurnTrackerContract.Commands>()

        val listOfTurnTrackerInputStates = tx.inputsOfType<TurnTrackerState>()
        val listOfTurnTrackerOutputStates = tx.outputsOfType<TurnTrackerState>()
        val newTurnTrackerState = listOfTurnTrackerOutputStates.first()

        when (command.value) {

            is Commands.EndTurn -> requireThat {
                val oldTurnTrackerState = tx.inputsOfType<TurnTrackerState>().firstOrNull() as TurnTrackerState
                "There must be exactly one Turn Tracker input state." using (listOfTurnTrackerInputStates.size == 1)
                "There must be exactly one Turn Tracker output state." using (listOfTurnTrackerOutputStates.size == 1)
                "Both setup rounds must have been completed" using (newTurnTrackerState.setUpRound1Complete && newTurnTrackerState.setUpRound2Complete)
                "The turn tracker must be incremented to 4 and then reset." using (
                        if (oldTurnTrackerState.currTurnIndex < 3 && (newTurnTrackerState.currTurnIndex + 1 == oldTurnTrackerState.currTurnIndex))
                            (oldTurnTrackerState.currTurnIndex == 3 && newTurnTrackerState.currTurnIndex == 0)
                        else false)
            }

            is Commands.EndTurnDuringInitialPlacementPhase -> requireThat {
                val oldTurnTrackerState = tx.inputsOfType<TurnTrackerState>().firstOrNull() as TurnTrackerState
                "There must be exactly one Turn Tracker input state." using (listOfTurnTrackerInputStates.size == 1)
                "There must be exactly one Turn Tracker output state." using (listOfTurnTrackerOutputStates.size == 1)
                if (newTurnTrackerState.setUpRound2Complete) {
                    "The turn tracker must be decremented to reflect the reverse placement order" using (oldTurnTrackerState.currTurnIndex > newTurnTrackerState.currTurnIndex)
                }
                if (newTurnTrackerState.setUpRound1Complete) {
                    "The turn tracker must be incremented to reflect the placement order" using (oldTurnTrackerState.currTurnIndex > newTurnTrackerState.currTurnIndex)
                }
            }

            is Commands.CreateTurnTracker -> requireThat {
                "There must be exactly one Turn Tracker output state." using (listOfTurnTrackerOutputStates.size == 1)
                "The turn tracker must be initialized at a 0 index." using (newTurnTrackerState.currTurnIndex == 0)
            }

        }


    }

    interface Commands : CommandData {
        class EndTurn: Commands
        class EndTurnDuringInitialPlacementPhase: Commands
        class CreateTurnTracker: Commands
    }

}