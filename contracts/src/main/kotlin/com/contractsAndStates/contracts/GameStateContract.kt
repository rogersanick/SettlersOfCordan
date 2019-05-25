package com.contractsAndStates.contracts

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.SettlementState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

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

        when (command.value) {

            is Commands.SetUpGameBoard -> requireThat {
                /**
                 *  ******** SHAPE ********
                 */

                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                /**
                 *  ******** SIGNATURES ********
                 */
            }

            is Commands.WinGame -> requireThat {

                val inputGameBoardState = tx.inputsOfType<GameBoardState>()

                /**
                 *  ******** SHAPE ********
                 */

                "There is one input and it is of type GameBoardState" using (inputGameBoardState.size == 1)
                "There is one output and it is of type GameBoardState" using (tx.outputsOfType<GameBoardState>().size == 1)

                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                "The claiming victor has the appropriate number of victory points" using (tx.referenceInputRefsOfType<SettlementState>().sumBy { it.state.data.resourceAmountClaim } >= 10)

                /**
                 *  ******** SIGNATURES ********
                 */

                val signingParties = tx.commandsOfType<Commands.WinGame>().single().signers.toSet()
                val participants = inputGameBoardState.single().participants.map{ it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using(signingParties.containsAll<PublicKey>(participants) && signingParties.size == 4)

            }

            is Commands.UpdateWithSettlement -> requireThat {
                /**
                 *  ******** SHAPE ********
                 */

                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                /**
                 *  ******** SIGNATURES ********
                 */
            }

        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class SetUpGameBoard : Commands
        class WinGame: Commands
        class UpdateWithSettlement: Commands
    }
}