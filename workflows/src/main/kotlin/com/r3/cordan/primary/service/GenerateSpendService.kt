package com.r3.cordan.primary.service

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.contracts.utilities.sumTokenStateAndRefs
import com.r3.corda.lib.tokens.contracts.utilities.withoutIssuer
import com.r3.corda.lib.tokens.workflows.flows.redeem.addTokensToRedeem
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import com.r3.corda.lib.tokens.workflows.utilities.heldTokenAmountCriteria
import com.r3.cordan.primary.states.resources.GameCurrencyState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.TransactionBuilder

@CordaService
class GenerateSpendService(val serviceHub: AppServiceHub): SingletonSerializeAsToken() {

    /**
     * When a player spends tokens in-game, those tokens are consumed as inputs to a transaction. The generateInGameSpend
     * method leverages the token-SDK to build a transaction proposing the consumption of tokens when they are
     * spent (burned) and not transferred to a counter-party. This is useful for simulating in game spend events where
     * resources (tokens) would be removed from play.
     *
     * This method uses the [TokenSelection.attemptSpend], [TokenSelection.generateExit], and [addTokensToRedeem] functions
     * sequentially to mutates an inputted transaction builder in place.
     *
     * @param tb The transaction builder in which we wish to consumer tokens and issue change to the spender
     * @param costs A map representing the [Amount] of each [TokenType] we should spend to satisfy the cost of our action
     * @param holder The holder of all the tokens that should be selected and spent
     * @param changeOwner The recipient of any change resulting from the spend
     * @param additionalQueryCriteria Additional [QueryCriteria] that will be appended to the selection process for tokens
     * that will be spent.
     * @return A transaction builder with inputs, outputs and commands adjusted to reflect the in game spend.
     */

    fun generateInGameSpend(
            gameBoardId: UniqueIdentifier,
            tb: TransactionBuilder,
            costs: Map<TokenType, Long>,
            holder: Party,
            changeOwner: Party,
            additionalQueryCriteria: QueryCriteria? = null
    ): TransactionBuilder {

        // Generate exits for tokens of the appropriate type
        costs.filter { it.value > 0 }.forEach { (type, amount) ->

            val baseCriteria = heldTokenAmountCriteria(type, holder)
            val queryCriteria = additionalQueryCriteria?.let { baseCriteria.and(it) } ?: baseCriteria

            // Get a list of tokens satisfying the costs
            val tokensToSpend = TokenSelection(serviceHub)
                    .attemptSpend(amount of type, tb.lockId, queryCriteria)

            // Run checks on the tokens to ensure the proposed transaction is valid
            val notaryToCheck = tokensToSpend.first().state.notary
            check(tokensToSpend.all { it.state.notary == notaryToCheck }) { "You are trying to spend assets with different notaries." }
            check(tokensToSpend.isNotEmpty()) { "Received empty list of states to spend." }

            // Begin to spend tokens to satisfy costs
            var spentAmount = Amount(0, type)
            tokensToSpend
                .groupBy { it.state.data.issuer }
                .forEach {
                    val sumOfTokensInGroup = it.value.sumTokenStateAndRefs().withoutIssuer()
                    val adjustedAmountToSpend = if (spentAmount.plus(sumOfTokensInGroup).quantity > costs[type]!!) {
                        Amount(amount.minus(spentAmount.quantity), type)
                    } else {
                        sumOfTokensInGroup
                    }
                    val (exitStates, change) = TokenSelection(serviceHub).generateExit(
                            it.value,
                            adjustedAmountToSpend,
                            changeOwner)
                    spentAmount = spentAmount.plus(sumOfTokensInGroup)
                    addTokensToRedeem(tb, exitStates, if (change != null) GameCurrencyState(change, gameBoardId) else null )
                }
        }

        // Return the mutated transaction builder
        return tb
    }
}