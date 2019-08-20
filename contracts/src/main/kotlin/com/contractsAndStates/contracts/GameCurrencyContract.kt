package com.contractsAndStates.contracts

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.GameCurrencyState
import com.r3.corda.lib.tokens.contracts.AbstractTokenContract
import com.r3.corda.lib.tokens.contracts.FungibleTokenContract
import com.r3.corda.lib.tokens.contracts.commands.TokenCommand
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.*
import net.corda.core.internal.uncheckedCast

class GameCurrencyContract(val gameBoardState: GameBoardState): AbstractTokenContract<GameCurrencyState>() {

    override val accepts: Class<GameCurrencyState> get() = uncheckedCast(FungibleToken::class.java)

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun verifyIssue(issueCommand: CommandWithParties<TokenCommand>, inputs: List<IndexedState<GameCurrencyState>>, outputs: List<IndexedState<GameCurrencyState>>, attachments: List<Attachment>) {
        FungibleTokenContract().verifyIssue(issueCommand, inputs, outputs, attachments)
        requireThat {
            "All of the players for the relevant game board must sign the transaction" using (issueCommand.signers.containsAll(gameBoardState.players.map { it.owningKey }) && issueCommand.signers.size == gameBoardState.players.size)
            "All of the input tokens should be for the same game board" using inputs.all { it.state.data.gameBoardId == inputs.first().state.data.gameBoardId }
            "All of the output tokens should be for the same game board" using outputs.all { it.state.data.gameBoardId == outputs.first().state.data.gameBoardId }
            "All of the tokens should be for the specified game board" using (outputs + inputs).all { it.state.data.gameBoardId == gameBoardState.linearId }
        }
    }

    override fun verifyMove(moveCommands: List<CommandWithParties<TokenCommand>>, inputs: List<IndexedState<GameCurrencyState>>, outputs: List<IndexedState<GameCurrencyState>>, attachments: List<Attachment>) {
        FungibleTokenContract().verifyMove(moveCommands, inputs, outputs, attachments)
        requireThat {
            "All of the players for the relevant game board must sign the transaction" using moveCommands.all { (it.signers.containsAll(gameBoardState.players.map { it.owningKey }) && it.signers.size == gameBoardState.players.size) }
            "All of the input tokens should be for the same game board" using inputs.all { it.state.data.gameBoardId == inputs.first().state.data.gameBoardId }
            "All of the output tokens should be for the same game board" using outputs.all { it.state.data.gameBoardId == outputs.first().state.data.gameBoardId }
            "All of the tokens should be for the specified game board" using (outputs + inputs).all { it.state.data.gameBoardId == gameBoardState.linearId }
        }
    }

    override fun verifyRedeem(redeemCommand: CommandWithParties<TokenCommand>, inputs: List<IndexedState<GameCurrencyState>>, outputs: List<IndexedState<GameCurrencyState>>, attachments: List<Attachment>) {
        FungibleTokenContract().verifyRedeem(redeemCommand, inputs, outputs, attachments)
        requireThat {
            "All of the players for the relevant game board must sign the transaction" using (redeemCommand.signers.containsAll(gameBoardState.players.map { it.owningKey }) && redeemCommand.signers.size == gameBoardState.players.size)
            "All of the input tokens should be for the same game board" using inputs.all { it.state.data.gameBoardId == inputs.first().state.data.gameBoardId }
            "All of the output tokens should be for the same game board" using outputs.all { it.state.data.gameBoardId == outputs.first().state.data.gameBoardId }
            "All of the tokens should be for the specified game board" using (outputs + inputs).all { it.state.data.gameBoardId == gameBoardState.linearId }
        }
    }

}