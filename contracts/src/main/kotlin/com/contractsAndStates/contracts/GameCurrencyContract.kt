package com.contractsAndStates.contracts

import com.contractsAndStates.states.*
import com.r3.corda.lib.tokens.contracts.AbstractTokenContract
import com.r3.corda.lib.tokens.contracts.FungibleTokenContract
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.RedeemTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.TokenCommand
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.utilities.sumTokenStatesOrZero
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class GameCurrencyContract: AbstractTokenContract<GameCurrencyState>(), Contract {

    override val accepts: Class<GameCurrencyState> get() = uncheckedCast(FungibleToken::class.java)

    companion object {
        val contractId = "com.contractsAndStates.contracts.GameCurrencyContract"
    }

    override fun verifyIssue(issueCommand: CommandWithParties<TokenCommand>, inputs: List<IndexedState<GameCurrencyState>>, outputs: List<IndexedState<GameCurrencyState>>, attachments: List<Attachment>, references: List<StateAndRef<ContractState>>) {
        // This code is a replication of the FungibleTokenContract verifyIssue function with one small change. In this case, currency must be issued
        val issuedToken: IssuedTokenType = issueCommand.value.token
        require(inputs.isEmpty()) { "When issuing tokens, there cannot be any input states." }
        outputs.apply {
            require(isNotEmpty()) { "When issuing tokens, there must be output states." }
            // We don't care about the token as the grouping function ensures that all the outputs are of the same
            // token.
            require(this.map { it.state.data }.sumTokenStatesOrZero(issuedToken) > Amount.zero(issuedToken)) {
                "When issuing tokens an amount > ZERO must be issued."
            }
            val hasZeroAmounts = any { it.state.data.amount == Amount.zero(issuedToken) }
            require(hasZeroAmounts.not()) { "You cannot issue tokens with a zero amount." }

            val issuerKeys: List<PublicKey> = this.map { it.state.data }.map(AbstractToken::issuer).toSet().map { it.owningKey }
            val issueSigners: List<PublicKey> = issueCommand.signers
            // The issuer should be signing the issue command. Notice that it can be signed by more parties.
            require(issuerKeys.all { issuerKey -> issuerKey in issueSigners}) {
                "The issuer must be the signing party when an amount of tokens are issued."
            }
        }

        requireThat {
            "There should be at least four players signing token issuance to prevent collusion" using (issueCommand.signers.toSet().size > 3)
            "All of the input tokens should be for the same game board" using inputs.all { it.state.data.gameBoardId == inputs.first().state.data.gameBoardId }
            "All of the output tokens should be for the same game board" using outputs.all { it.state.data.gameBoardId == outputs.first().state.data.gameBoardId }
            "All of the input / output tokens should be of an acceptable token type" using (inputs + outputs).all { listOf( Wood, Wheat, Brick, Ore, Sheep ).contains(it.state.data.issuedTokenType.tokenType) }
        }
    }

    override fun verifyMove(moveCommands: List<CommandWithParties<TokenCommand>>, inputs: List<IndexedState<GameCurrencyState>>, outputs: List<IndexedState<GameCurrencyState>>, attachments: List<Attachment>, references: List<StateAndRef<ContractState>>) {
        FungibleTokenContract().verifyMove(moveCommands, inputs, outputs, attachments, references)
        requireThat {
            "All of the input tokens should be for the same game board" using inputs.all { it.state.data.gameBoardId == inputs.first().state.data.gameBoardId }
            "All of the output tokens should be for the same game board" using outputs.all { it.state.data.gameBoardId == outputs.first().state.data.gameBoardId }
            "All of the input / output tokens should be of an acceptable token type" using (inputs + outputs).all { listOf( Wood, Wheat, Brick, Ore, Sheep ).contains(it.state.data.issuedTokenType.tokenType) }
        }
    }

    override fun verifyRedeem(redeemCommand: CommandWithParties<TokenCommand>, inputs: List<IndexedState<GameCurrencyState>>, outputs: List<IndexedState<GameCurrencyState>>, attachments: List<Attachment>, references: List<StateAndRef<ContractState>>) {
        FungibleTokenContract().verifyRedeem(redeemCommand, inputs, outputs, attachments, references)
        requireThat {
            "All of the input tokens should be for the same game board" using inputs.all { it.state.data.gameBoardId == inputs.first().state.data.gameBoardId }
            "All of the output tokens should be for the same game board" using outputs.all { it.state.data.gameBoardId == outputs.first().state.data.gameBoardId }
            "All of the input / output tokens should be of an acceptable token type" using (inputs + outputs).all { listOf( Wood, Wheat, Brick, Ore, Sheep ).contains(it.state.data.issuedTokenType.tokenType) }
        }
    }

}