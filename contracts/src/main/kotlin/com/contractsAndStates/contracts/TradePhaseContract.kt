package com.contractsAndStates.contracts

import com.contractsAndStates.states.*
import net.corda.core.contracts.*
import net.corda.core.internal.sumByLong
import net.corda.core.transactions.LedgerTransaction

// *************************
// * Gather Phase Contract *
// *************************

class TradePhaseContract : Contract {
    companion object {
        const val ID = "com.contractsAndStates.contracts.TradePhaseContract"
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {
            is Commands.TradeWithPort -> requireThat {

                val gameBoardState = tx.referenceInputsOfType<GameBoardState>().first()
                val turnTrackerState = tx.referenceInputsOfType<TurnTrackerState>().first()

                val inputTokens = tx.inputsOfType<GameCurrencyState>()
                val outputTokens = tx.outputsOfType<GameCurrencyState>()

                val portHexTileLocation = (command.value as Commands.TradeWithPort).tileOfPort
                val portTileCornerIndex = (command.value as Commands.TradeWithPort).cornerOfPort
                val port = gameBoardState.ports.getPortAt(portHexTileLocation, portTileCornerIndex)
                /**
                 *  ******** SHAPE ********
                 */

                //TODO: Refactor contract and flow so that trades with ports may contain any combination / multiple of acceptable resources.

                "There should be a referenced GameBoardState" using (tx.referenceInputsOfType<GameBoardState>().size == 1)
                "There should be a referenced TurnTrackerState" using (tx.referenceInputsOfType<TurnTrackerState>().size == 1)
                "There should be input tokens in the transaction" using (tx.inputsOfType<GameCurrencyState>().isNotEmpty())
                "All input tokens should be of the same type" using (inputTokens.all { it.fungibleToken.issuedTokenType.tokenType == inputTokens.first().issuedTokenType.tokenType })
                "There should be output tokens in the transaction" using (tx.outputsOfType<GameCurrencyState>().isNotEmpty())
                "All output tokens should be of the same type" using (outputTokens.all { it.fungibleToken.issuedTokenType.tokenType == outputTokens.first().issuedTokenType.tokenType })

                /**
                 *  ******** BUSINESS LOGIC ********
                 */
                "The turn tracker referenced should be for the included game board" using (gameBoardState.turnTrackerLinearId == turnTrackerState.linearId)
                "The port should be valid for this game board" using (gameBoardState.ports.checkPortAt(portHexTileLocation, portTileCornerIndex))

                val inputTokenAmount = Amount(inputTokens.sumByLong { it.fungibleToken.amount.quantity }, inputTokens.first().tokenType)
                val outputTokenAmount = Amount(inputTokens.sumByLong { it.fungibleToken.amount.quantity }, outputTokens.first().tokenType)
                "The provided input tokens should match those required by the port" using (port.portTile.inputRequired.contains(inputTokenAmount))
                "The provided output tokens should match those required by the port" using (port.portTile.inputRequired.contains(outputTokenAmount))

                /**
                 *  ******** SIGNATURES ********
                 */
                val signingParties = command.signers.toSet()
                val participants = gameBoardState.participants.map { it.owningKey }
                "All players must verify and sign the transaction to build a settlement." using
                        (signingParties.containsAll(participants) && signingParties.size == 4)

            }

            is Commands.ExecuteTrade -> requireThat {
                "check" using (true)
            }

        }


    }

    interface Commands : CommandData {
        class TradeWithPort(
                val tileOfPort: HexTileIndex,
                val cornerOfPort: TileCornerIndex
        ): Commands
        class IssueTrade: Commands
        class CancelTrade: Commands
        class ExecuteTrade: Commands
    }
}