package com.oracleService.contracts

import com.oracleService.state.DiceRollState
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
        when (tx.commands.single().value) {
            is Commands.RollDice -> {
                requireThat {
                    "There are no inputs" using (tx.inputStates.isEmpty())
                    "There is exactly one output" using (tx.outputStates.size == 1)

                    val diceRollState = tx.outputStates[0] as DiceRollState
                    "The output state is a DiceRollState" using (diceRollState.randomRoll1 in 1..6)
                    "The output state is a DiceRollState" using (diceRollState.randomRoll2 in 1..6)
                }
            }
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class RollDice : Commands
    }
}