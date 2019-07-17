package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.RobberState
import com.contractsAndStates.states.TurnTrackerState
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.redeem.addFungibleTokensToRedeem
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

/**
 * When a player spends resources in-game, those resources are consumed as inputs to a transaction. The generateInGameSpend
 * method leverages the token-SDK to facilitate the building of transaction proposing the consumption of tokens when they are
 * spent (burned) and not transferred to a counter-party.
 *
 * This method uses the generateExit functionality from the tokenSelection and mutates an input transaction builder in place.
 */

@Suspendable
fun generateInGameSpend(
        serviceHub: ServiceHub,
        tb: TransactionBuilder,
        costs: Map<TokenType, Long>,
        changeOwner: Party
): TransactionBuilder {

    // Create a tokenSelector
    val tokenSelection = TokenSelection(serviceHub)

    // Generate exits for tokens of the appropriate type
    costs.forEach { (type, amount) ->
        tokenSelection
                .attemptSpend(amount of type, tb.lockId)
                .groupBy { it.state.data.issuer }
                .forEach {
                    addFungibleTokensToRedeem(tb, serviceHub, amount of type, it.key, changeOwner)
                }
    }

    // Return the mutated transaction builder
    return tb
}

inline fun <reified T : ContractState> getSingleStateByLinearId(linearId: UniqueIdentifier, serviceHub: ServiceHub) =
        QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId)).let { criteria ->
            serviceHub.vaultService.queryBy<T>(criteria).states.single()
        }

fun getGameBoardStateFromLinearID(linearId: UniqueIdentifier, serviceHub: ServiceHub) =
        getSingleStateByLinearId<GameBoardState>(linearId, serviceHub)

fun getTurnTrackerStateFromLinearID(linearId: UniqueIdentifier, serviceHub: ServiceHub) =
        getSingleStateByLinearId<TurnTrackerState>(linearId, serviceHub)

fun getRobberStateFromLinearID(linearId: UniqueIdentifier, serviceHub: ServiceHub) =
        getSingleStateByLinearId<RobberState>(linearId, serviceHub)
