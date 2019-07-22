package com.contractsAndStates.contracts

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.SettlementState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

// ***********************
// * Game State Contract *
// ***********************
class GameStateContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.contractsAndStates.contracts.GameStateContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()
        val inputGameBoardStates = tx.inputsOfType<GameBoardState>()
        val outputGameBoardStates = tx.outputsOfType<GameBoardState>()

        when (command.value) {

            is Commands.SetUpGameBoard -> requireThat {
                /**
                 *  ******** SHAPE ********
                 */

                "There must be one output of type GameBoardState" using (outputGameBoardStates.size == 1)

                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                /**
                 *  ******** SIGNATURES ********
                 */

                val signingParties = command.signers.toSet()
                val participants = outputGameBoardStates.single().participants.map { it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using
                        (signingParties.containsAll(participants) && signingParties.size == 4)
            }

            is Commands.WinGame -> requireThat {

                /**
                 *  ******** SHAPE ********
                 */

                "There must be one output" using (tx.outputs.size == 1)
                "There must be one input of type GameBoardState" using (inputGameBoardStates.size == 1)
                "There must be one output of type GameBoardState" using (outputGameBoardStates.size == 1)

                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                "The claiming victor has the appropriate number of victory points" using
                        (tx.referenceInputRefsOfType<SettlementState>().sumBy { it.state.data.resourceAmountClaim } >= 10)

                /**
                 *  ******** SIGNATURES ********
                 */

                val signingParties = command.signers.toSet()
                val participants = inputGameBoardStates.single().participants.map { it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using
                        (signingParties.containsAll(participants) && signingParties.size == 4)
            }

            is Commands.UpdateWithSettlement -> requireThat {
                /**
                 *  ******** SHAPE ********
                 */

                "There is one input and it is of type GameBoardState" using (inputGameBoardStates.size == 1)
                "There is one output and it is of type GameBoardState" using (outputGameBoardStates.size == 1)
                val inputGameBoardState = inputGameBoardStates.single()

                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                // TODO: Verify that the input and output game board settlements are updating correctly.
                "There should be exactly one more input settlements than output settlements" using
                        (inputGameBoardState.getSettlementsCount() + 1 ==
                                outputGameBoardStates.single().getSettlementsCount())

                /**
                 *  ******** SIGNATURES ********
                 */

                val signingParties = command.signers.toSet()
                val participants = inputGameBoardState.participants.map { it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using
                        (signingParties.containsAll(participants) && signingParties.size == 4)
            }

        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class SetUpGameBoard : Commands
        class WinGame : Commands
        class UpdateWithSettlement : Commands
    }
}