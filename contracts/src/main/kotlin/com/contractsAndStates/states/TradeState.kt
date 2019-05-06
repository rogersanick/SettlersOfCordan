package com.contractsAndStates.states

import com.contractsAndStates.contracts.TradePhaseContract
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.DealState

@CordaSerializable
@BelongsToContract(TradePhaseContract::class)
data class TradeState (
        override val offering: Amount<TokenType>,
        override val wanted: Amount<TokenType>,
        override val owner: Party,
        val targetPlayer: Party,
        val players: List<Party>,
        val executed: Boolean,
        override val gameBoardStateLinearId: UniqueIdentifier,
        override val informationForAcceptor: InformationForAcceptor? = null,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
): LinearState, ExtendedDealState {

    override fun generateAgreement(transactionBuilder: TransactionBuilder): TransactionBuilder {
        val state = TradeState(offering, wanted, owner, targetPlayer, players, executed, gameBoardStateLinearId, linearId = linearId)
        return transactionBuilder.withItems(
                StateAndContract(state, TradePhaseContract.ID),
                Command(TradePhaseContract.Commands.ExecuteTrade(), participants.map { it.owningKey })
        )
    }

    override fun generateAgreement(notary: Party): TransactionBuilder { return TransactionBuilder() }

    override val participants: List<AbstractParty> get() = listOf(owner, targetPlayer)
}

interface ExtendedDealState: DealState {
    fun generateAgreement(transactionBuilder: TransactionBuilder): TransactionBuilder
    val informationForAcceptor: InformationForAcceptor?
    val offering: Amount<TokenType>
    val wanted: Amount<TokenType>
    val owner: Party
    val gameBoardStateLinearId: UniqueIdentifier
}

@CordaSerializable
class InformationForAcceptor(
        val inputStates: List<StateRef>,
        val outputStates: List<TransactionState<*>>,
        val commands: List<Command<*>>
)
