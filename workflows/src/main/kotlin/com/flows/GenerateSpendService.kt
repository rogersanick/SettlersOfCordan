package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.contracts.utilities.sumTokenStateAndRefs
import com.r3.corda.lib.tokens.contracts.utilities.withoutIssuer
import com.r3.corda.lib.tokens.workflows.flows.redeem.addTokensToRedeem
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import com.r3.corda.lib.tokens.workflows.utilities.heldTokenAmountCriteria
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.TransactionBuilder
import org.slf4j.Logger

@CordaService
class GenerateSpendService(serviceHub: AppServiceHub): SingletonSerializeAsToken() {
    /**
     * When a player spends resources in-game, those resources are consumed as inputs to a transaction. The generateInGameSpend
     * method leverages the token-SDK to facilitate the building of transaction proposing the consumption of tokens when they are
     * spent (burned) and not transferred to a counter-party.
     *
     * This method uses the generateExit functionality from the tokenSelection and mutates an input transaction builder in place.
     */

    fun generateInGameSpend(
            serviceHub: ServiceHub,
            tb: TransactionBuilder,
            costs: Map<TokenType, Long>,
            holder: Party,
            changeOwner: Party,
            additionalQueryCriteria: QueryCriteria? = null
    ): TransactionBuilder {

        // Create a tokenSelector
        val tokenSelection = TokenSelection(serviceHub)

        // Generate exits for tokens of the appropriate type
        costs.filter { it.value > 0 }.forEach { (type, amount) ->

            val baseCriteria = heldTokenAmountCriteria(type, holder)
            val queryCriteria = additionalQueryCriteria?.let { baseCriteria.and(it) } ?: baseCriteria

            // Get a list of tokens satisfying the costs
            val tokensToSpend = tokenSelection
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
                        val amountOfTokens = it.value.sumTokenStateAndRefs().withoutIssuer()
                        spentAmount = spentAmount.plus(amountOfTokens)
                        val (exitStates, change) = tokenSelection.generateExit(
                                it.value,
                                if (spentAmount.quantity > costs[type]!!) Amount(amount, type) else amountOfTokens,
                                changeOwner)
                        addTokensToRedeem(tb, exitStates, change)
                    }
        }

        // Return the mutated transaction builder
        return tb
    }
}