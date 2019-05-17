package com.flows

import com.contractsAndStates.states.*
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.selection.TokenSelection
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder

class CorDanFlowUtils {

    companion object {

        val cityPrice = mapOf(
                Pair(Wheat, Amount(2, Wheat)),
                Pair(Wheat, Amount(3, Ore))
        )

        val settlementPrice = mapOf(
                Pair(Brick, Amount(1, Brick)),
                Pair(Wheat, Amount(1, Wheat)),
                Pair(Wood, Amount(1, Wood)),
                Pair(Sheep, Amount(1, Sheep))
        )

        val developmentCardPrice = mapOf(
                Pair(Sheep, Amount(1, Sheep)),
                Pair(Wheat, Amount(1, Wheat)),
                Pair(Ore, Amount(1, Ore))
        )
    }

}

fun generateInGameSpend(serviceHub: ServiceHub, tb: TransactionBuilder, price: Map<Resource, Amount<Resource>>, changeOwner: Party): TransactionBuilder {

    // Create a tokenSelector
    val tokenSelection = TokenSelection(serviceHub)

    // Generate exits for tokens of the appropriate type
    price.forEach { tokenType, amount ->
        val tokensToSpend = tokenSelection.attemptSpend(amount, tb.lockId).groupBy { it.state.data.issuer }
        tokensToSpend.forEach {
            var amountToSpendForSpecificIssuer: Long = 0
            it.value.forEach { issuedAmounts -> amountToSpendForSpecificIssuer += issuedAmounts.state.data.amount.quantity }
            tokenSelection.generateExit(tb, it.value, Amount(amountToSpendForSpecificIssuer, tokenType), changeOwner)
        }
    }

    // Return the mutated transaction builder
    return tb

}