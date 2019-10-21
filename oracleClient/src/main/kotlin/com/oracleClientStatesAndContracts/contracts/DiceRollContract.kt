package com.oracleClientStatesAndContracts.contracts

import com.oracleClientStatesAndContracts.states.DiceRollState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class DiceRollContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.oracleService.contracts.DiceRollContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        when (tx.commandsOfType<Commands>().single().value) {
            is Commands.RollDice -> {
                requireThat {
                    "There are no inputs" using (tx.inputStates.isEmpty())
                    "There is exactly one output" using (tx.outputStates.size == 1)

                    val diceRollState = tx.outputStates[0] as DiceRollState
                    "The output dice roll state must have between 1 and 6 pips (representing an int value)" using (diceRollState.randomRoll1 in 1..6)
                    "The output dice roll state must have between 1 and 6 pips (representing an int value)" using (diceRollState.randomRoll2 in 1..6)
                }
            }

            is Commands.ConsumeDiceRoll -> {
                requireThat {
                    "There is one DiceRollState input" using (tx.inputsOfType<DiceRollState>().size == 1)
                    "There is are no DiceRollStateOutputs" using (tx.outputsOfType<DiceRollState>().isEmpty())
                }
            }

        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class RollDice : Commands
        class ConsumeDiceRoll: Commands
    }
}