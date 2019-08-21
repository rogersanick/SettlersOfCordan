package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.states.BelongsToGameBoard
import com.contractsAndStates.states.HasGameBoardId
import com.oracleClientStatesAndContracts.states.DiceRollState
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.redeem.addFungibleTokensToRedeem
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

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

@Suspendable
fun addIssueTokens(transactionBuilder: TransactionBuilder, outputs: List<AbstractToken>, additionalSigners: Collection<PublicKey>): TransactionBuilder {
    val outputGroups: Map<IssuedTokenType, List<AbstractToken>> = outputs.groupBy { it.issuedTokenType }
    return transactionBuilder.apply {
        outputGroups.forEach { (issuedTokenType: IssuedTokenType, states: List<AbstractToken>) ->
            val issuers = states.map { it.issuer }.toSet()
            require(issuers.size == 1) { "All tokensToIssue must have the same issuer." }
            val issuer = issuers.single()
            var startingIndex = outputStates().size
            val indexesAdded = states.map { state ->
                addOutputState(state)
                startingIndex++
            }
            addCommand(IssueTokenCommand(issuedTokenType, indexesAdded), additionalSigners + issuer.owningKey)
        }
    }
}

inline fun <reified T : ContractState> VaultService.querySingleState(linearId: UniqueIdentifier) =
        QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
                .let { criteria ->
                    queryBy<T>(criteria).states
                }
                .also { stateAndRefs ->
                    if (stateAndRefs.size != 1) {
                        throw FlowException("There should be a single ${T::class.simpleName} of id $linearId, but found ${stateAndRefs.size}")
                    }
                }
                .single()

inline fun <reified T : ContractState> VaultService.querySingleState(stateRef: StateRef): StateAndRef<T> =
        querySingleState(listOf(stateRef))

inline fun <reified T : ContractState> VaultService.querySingleState(stateRefs: List<StateRef>) =
        QueryCriteria.VaultQueryCriteria(stateRefs = stateRefs)
                .let { criteria ->
                    queryBy<T>(criteria).states
                }
                .also { stateAndRefs ->
                    if (stateAndRefs.size != 1) {
                        throw FlowException("There should be a single ${T::class.simpleName} for this ref, but found ${stateAndRefs.size}")
                    }
                }
                .single()

inline fun <reified T> VaultService.queryBelongsToGameBoard(gameBoardLinearId: UniqueIdentifier)
        where T : ContractState, T : HasGameBoardId =
        builder {
            BelongsToGameBoard::gameBoardLinearId.equal(gameBoardLinearId)
        }.let { predicate ->
            QueryCriteria.VaultCustomQueryCriteria(predicate)
        }.let { criteria ->
            queryBy<T>(criteria).states
        }

fun VaultService.queryDiceRoll(gameBoardLinearId: UniqueIdentifier) =
        queryBy<DiceRollState>()
                .states
                .filter {
                    it.state.data.gameBoardLinearId == gameBoardLinearId
                }
                .also { stateAndRefs ->
                    if (stateAndRefs.size != 1) {
                        throw FlowException("There should be a single DiceRollState for id $gameBoardLinearId, but found ${stateAndRefs.size}")
                    }
                }
                .single()