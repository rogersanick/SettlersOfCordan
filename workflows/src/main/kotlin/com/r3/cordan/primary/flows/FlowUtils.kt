package com.r3.cordan.primary.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.cordan.oracle.client.states.DiceRollState
import com.r3.cordan.primary.states.board.GameBoardSchemaV1
import com.r3.cordan.primary.states.resources.GameCurrencyState
import com.r3.cordan.primary.states.board.HasGameBoardIdSchema
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey
import java.util.*

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

inline fun <reified T : ContractState> VaultService.querySingleState(stateRef: StateRef, stateStatus: Vault.StateStatus = Vault.StateStatus.UNCONSUMED): StateAndRef<T> =
        querySingleState(listOf(stateRef), stateStatus)

inline fun <reified T : ContractState> VaultService.querySingleState(stateRefs: List<StateRef>, stateStatus: Vault.StateStatus = Vault.StateStatus.UNCONSUMED) =
        QueryCriteria.VaultQueryCriteria(stateRefs = stateRefs, status = stateStatus)
                .let { criteria ->
                    queryBy<T>(criteria).states
                }
                .also { stateAndRefs ->
                    if (stateAndRefs.size != 1) {
                        throw FlowException("There should be a single ${T::class.simpleName} for this ref, but found ${stateAndRefs.size}")
                    }
                }
                .single()

inline fun <reified T> VaultService.queryBelongsToGameBoard(gameBoardLinearId: UniqueIdentifier, persistentState: PersistentState)
                where T : ContractState, T: QueryableState =
                builder {
                    GameBoardSchemaV1.PersistentGameBoardState::linearId.equal(gameBoardLinearId.toString())
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

@Suspendable
@JvmOverloads
inline fun <reified T: FungibleToken> TokenSelection.generateMoveGameCurrency(
        lockId: UUID,
        partyAndAmounts: List<PartyAndAmount<TokenType>>,
        changeHolder: AbstractParty,
        gameBoardLinearId: UniqueIdentifier,
        queryCriteria: QueryCriteria? = null): Pair<List<StateAndRef<T>>, List<T>> {
    val fungibleTokens = this.generateMove(lockId, partyAndAmounts, changeHolder, queryCriteria)
    val listStateAndRefGameCurrency = fungibleTokens.first.map {
        it as StateAndRef<T>
    }
    val listOfGameCurrency = fungibleTokens.second.map { GameCurrencyState(it, gameBoardLinearId) as T }
    return Pair(listStateAndRefGameCurrency, listOfGameCurrency)
}