package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.states.*
import com.r3.corda.sdk.token.workflow.flows.internal.selection.TokenSelection
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder

class CorDanFlowUtils {

    /**
     * When building game elements in Settlers of Catan, the player must provide input resources. The companion object
     * below represents the effective rate card shared mutually amongst participants to verify proposed transactions
     * are consuming the appropriate number of resources.
     */
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

/**
 * When a player spends resources in-game, those resources are consumed as inputs to a transaction. The generateInGameSpend
 * method leverages the token-SDK to facilitate the building of transaction proposing the consumption of tokens when they are
 * spent (burned) and not transferred to a counter-party.
 *
 * This method uses the generateExit functionality from the tokenSelection and mutates an input transaction builder in place.
 */

@Suspendable
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

fun getGameBoardStateFromLinearID(linearId: UniqueIdentifier): GameBoardState {

}

fun getTurnTrackerStateFromGameBoardLinearID(linearId: UniqueIdentifier): TurnTrackerState {

}