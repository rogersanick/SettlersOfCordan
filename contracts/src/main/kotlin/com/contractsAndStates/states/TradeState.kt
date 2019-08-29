package com.contractsAndStates.states

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.TradePhaseContract
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.schemas.StatePersistable
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.DealState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

@CordaSerializable
@BelongsToContract(TradePhaseContract::class)
data class TradeState(
        override val offering: Amount<TokenType>,
        override val wanted: Amount<TokenType>,
        override val owner: Party,
        val targetPlayer: Party,
        val players: List<Party>,
        val executed: Boolean,
        override val gameBoardLinearId: UniqueIdentifier,
        override val informationForAcceptor: InformationForAcceptor? = null,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState, ExtendedDealState {

    @Suspendable
    override fun generateAgreement(transactionBuilder: TransactionBuilder): TransactionBuilder {
        val state = TradeState(offering, wanted, owner, targetPlayer, players, executed, gameBoardLinearId, linearId = linearId)
        return transactionBuilder.withItems(
                StateAndContract(state, TradePhaseContract.ID),
                Command(TradePhaseContract.Commands.ExecuteTrade(), players.map { it.owningKey })
        )
    }

    @Suspendable
    override fun generateAgreement(notary: Party): TransactionBuilder {
        return TransactionBuilder()
    }

    override val participants: List<AbstractParty> get() = listOf(owner, targetPlayer)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is TradeSchemaV1 -> TradeSchemaV1.PersistentTradeState(gameBoardLinearId.toString())
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        return listOf(TradeSchemaV1)
    }
}

interface ExtendedDealState : DealState, QueryableState, StatePersistable, HasGameBoardId {
    fun generateAgreement(transactionBuilder: TransactionBuilder): TransactionBuilder
    val informationForAcceptor: InformationForAcceptor?
    val offering: Amount<TokenType>
    val wanted: Amount<TokenType>
    val owner: Party
    override val gameBoardLinearId: UniqueIdentifier
}

@CordaSerializable
class InformationForAcceptor(
        val inputStates: List<StateAndRef<*>>,
        val outputStates: List<TransactionState<*>>,
        val commands: List<Command<*>>,
        val attachments: List<SecureHash>,
        val gameBoardLinearId: UniqueIdentifier,
        val tradeStateLinearId: UniqueIdentifier
)

object TradeSchema

@CordaSerializable
object TradeSchemaV1 : MappedSchema(
        schemaFamily = TradeSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentTradeState::class.java)
) {
    @Entity
    @Table(
            name = "contract_trade_states",
            indexes = [
                Index(name = "${BelongsToGameBoard.columnName}_idx", columnList = BelongsToGameBoard.columnName)
            ])
    class PersistentTradeState(
            @Column(name = BelongsToGameBoard.columnName, nullable = false)
            var gameBoardLinearId: String
    ) : PersistentState(), StatePersistable
}
