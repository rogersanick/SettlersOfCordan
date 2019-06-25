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
        val inputGameBoardState = tx.inputsOfType<GameBoardState>()
        val outputGameBoardState = tx.outputsOfType<GameBoardState>()

        when (command.value) {

            is Commands.SetUpGameBoard -> requireThat {
                /**
                 *  ******** SHAPE ********
                 */

                "There must be one output of type GameBoardState" using (tx.outputsOfType<GameBoardState>().size == 1)

                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                /**
                 *  ******** SIGNATURES ********
                 */

                val signingParties = tx.commandsOfType<Commands.SetUpGameBoard>().single().signers.toSet()
                val participants = outputGameBoardState.single().participants.map { it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using(signingParties.containsAll<PublicKey>(participants) && signingParties.size == 4)

            }

            is Commands.WinGame -> requireThat {

                /**
                 *  ******** SHAPE ********
                 */

                "There must be one output" using (tx.outputs.size == 1)
                "There must be one input of type GameBoardState" using (inputGameBoardState.size == 1)
                "There must be one output of type GameBoardState" using (outputGameBoardState.size == 1)

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

                "There is one input and it is of type GameBoardState" using (inputGameBoardState.size == 1)
                "There is one output and it is of type GameBoardState" using (tx.outputsOfType<GameBoardState>().size == 1)

                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                // TODO: Verify that the input and output game board settlements are updating correctly.
                val inputGameBoardSettlements = inputGameBoardState.single().settlementsPlaced.map { it.map { sub -> if(sub) 1 else 0 }.sum() }.sum()
                val outputGameBoardSettlements = outputGameBoardState.single().settlementsPlaced.map { it.map { sub -> if(sub) 1 else 0 }.sum() }.sum()

                /**
                 *  ******** SIGNATURES ********
                 */

                val signingParties = tx.commandsOfType<Commands.UpdateWithSettlement>().single().signers.toSet()
                val participants = inputGameBoardState.single().participants.map{ it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using(signingParties.containsAll<PublicKey>(participants) && signingParties.size == 4)


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