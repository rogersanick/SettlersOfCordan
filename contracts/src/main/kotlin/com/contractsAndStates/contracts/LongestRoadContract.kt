package com.contractsAndStates.contracts

import com.contractsAndStates.states.*
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class LongestRoadContract : Contract {

    companion object {
        const val ID = "com.contractsAndStates.contracts.LongestRoadContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()

        val longestRoadInputStates = tx.inputsOfType<LongestRoadState>().first()
        val roadsInputStates = tx.inputsOfType<RoadState>()
        val settlementsInputStates = tx.inputsOfType<SettlementState>()
        val inputGameBoardState = tx.inputsOfType<GameBoardState>().single()

        val outputLongestRoadState = tx.outputsOfType<LongestRoadState>().first()

        when (command.value) {
            is Commands.Claim -> requireThat {

                /**
                 *  ******** BUSINESS LOGIC ********
                 */

                val candidate = longestRoad(
                        inputGameBoardState.hexTiles, roadsInputStates, settlementsInputStates,
                        inputGameBoardState.players, longestRoadInputStates.holder)

                "Incorrect longest road holder." using (candidate == outputLongestRoadState.holder)


                /**
                 *  ******** SIGNATURES ********
                 */

                val signingParties = tx.commandsOfType<Commands.Claim>().single().signers.toSet()
                val participants = outputLongestRoadState.participants.map{ it.owningKey }

                "All players must verify and sign the transaction to build a settlement." using(
                        signingParties.containsAll<PublicKey>(participants) && signingParties.size == 4)
            }
        }
    }

    interface Commands : CommandData {
        class Claim: Commands
    }
}