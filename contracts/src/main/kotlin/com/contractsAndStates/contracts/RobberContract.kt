package com.contractsAndStates.contracts

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.RobberState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

// *************************
// * Turn Tracker Contract *
// *************************

class RobberContract : Contract {

    companion object {
        const val ID = "com.contractsAndStates.contracts.RobberContract"
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()

        val listOfRobberInputStates = tx.inputsOfType<RobberState>()
        val listOfRobberOutputStates = tx.outputsOfType<RobberState>()
        val outputRobberState = listOfRobberOutputStates.first()

        when (command.value) {

            is Commands.CreateRobber -> requireThat {

                val outputGameBoard = tx.outputsOfType<GameBoardState>().single()

                /**
                 *  ******** SHAPE ********
                 */

                "There must be exactly one output state of type Robber." using (listOfRobberOutputStates.size == 1)

                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                "The robber state must have the same gameBoardStateLinearID as the output game board" using (outputGameBoard.robberLinearId == outputRobberState.linearId)

                /**
                 *  ******** SIGNATURES ********
                 */

                val signingParties = tx.commandsOfType<Commands.CreateRobber>().single().signers.toSet()
                val participants = outputGameBoard.participants.map{ it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using(signingParties.containsAll<PublicKey>(participants) && signingParties.size == 4)

            }

            is Commands.MoveRobber -> requireThat {

                val referencedGameBoardState = tx.referenceInputRefsOfType<GameBoardState>().single().state.data

                /**
                 *  ******** SHAPE ********
                 */

                "There must be exactly one Robber input state." using (listOfRobberInputStates.size == 1)
                "There must be exactly one Robber output state." using (listOfRobberOutputStates.size == 1)

                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                "The robber state must have the same gameBoardStateLinearID as the output game board" using (referencedGameBoardState.robberLinearId == outputRobberState.linearId)

                /**
                 *  ******** SIGNATURES ********
                 */

                val signingParties = tx.commandsOfType<Commands.MoveRobber>().single().signers.toSet()
                val participants = referencedGameBoardState.participants.map{ it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using(signingParties.containsAll<PublicKey>(participants) && signingParties.size == 4)


            }
        }


    }

    interface Commands : CommandData {
        class CreateRobber: Commands
        class MoveRobber: Commands
    }

}