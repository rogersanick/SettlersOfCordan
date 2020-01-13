package com.r3.cordan.primary.contracts.development

import com.r3.cordan.primary.states.development.RevealedDevelopmentCardState
import com.r3.cordan.primary.states.development.DevelopmentCardType
import com.r3.cordan.primary.states.development.FaceDownDevelopmentCardState
import com.r3.cordan.primary.states.structure.GameBoardState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.DigitalSignature
import net.corda.core.transactions.LedgerTransaction

// *****************************
// * Development Card Contract *
// *****************************
class DevelopmentCardContract : Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.r3.cordan.primary.contracts.development.DevelopmentCardContract"
    }

    // A transaction is valid if the verify() function of the contract of all the transaction's input and output states
    // does not throw an exception.
    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()
        val gameBoardState = tx.referenceInputRefsOfType<GameBoardState>().single().state.data

        when (command.value) {
            is Commands.Issue -> {
                val issuedDevelopmentCard = tx.outputsOfType<FaceDownDevelopmentCardState>().single()
            }
            is Commands.Reveal -> requireThat {
                val revealedDevelopmentCard = tx.outputsOfType<RevealedDevelopmentCardState>().single()
                val castedCommand = command.value as Commands.Reveal
                val randInputFromPeers = castedCommand.randInputFromPeers
                val devCardType = DevelopmentCardType.values()[randInputFromPeers.sumBy { it.first } % 5]
                "The issued development card is of the appropriate type" using (revealedDevelopmentCard.cardType == devCardType)
                "All other players must have provided random input." using (randInputFromPeers.size == 3)
                "The list of players signing the data must be the same as those playing the game" using (
                        randInputFromPeers.map { it.second.by }.toSet()
                                == gameBoardState.players.map { it.owningKey }.toSet() - revealedDevelopmentCard.owner.owningKey)

                "All signatures must be valid" using randInputFromPeers.all { randInput ->
                    val dataToVerify = byteArrayOf(castedCommand.seed.toByte(), randInput.first.toByte())
                    randInput.second.isValid(dataToVerify)
                }
            }
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Issue: Commands
        class Reveal(
                val seed: Int,
                val randInputFromPeers: List<Pair<Int, DigitalSignature.WithKey>>
        ): Commands
    }
}