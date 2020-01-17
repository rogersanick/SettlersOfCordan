package com.r3.cordan.primary.states.structure

import com.r3.cordan.primary.contracts.structure.BuildPhaseContract
import com.r3.cordan.primary.states.board.AbsoluteCorner
import com.r3.cordan.primary.states.board.BelongsToGameBoard
import com.r3.cordan.primary.states.board.HasGameBoardId
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.schemas.StatePersistable
import net.corda.core.serialization.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

/**
 * Settlements are the fundamental structure one may build in Settlers of Cordan. They
 * give the owner the right to claim (self-issue) resources of a certain type based on
 * where the settlement was built. This issuance is based on the random random roll a player
 * receives from the random roll and uses to propose the outcome of the next Gather phase.
 */

@CordaSerializable
@BelongsToContract(BuildPhaseContract::class)
data class SettlementState(
        override val gameBoardLinearId: UniqueIdentifier,
        val absoluteCorner: AbsoluteCorner,
        val players: List<Party>,
        val owner: Party,
        val upgradedToCity: Boolean = false,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState, QueryableState, StatePersistable, HasGameBoardId {

    val resourceAmountClaim = if (upgradedToCity) cityAmountClaim
    else settlementAmountClaim

    init {
        require(resourceAmountClaim in listOf(settlementAmountClaim, cityAmountClaim)) {
            "resourceAmountClaim of $resourceAmountClaim is not an allowed value"
        }
    }

    companion object {
        const val settlementAmountClaim = 1
        const val cityAmountClaim = 2
    }

    fun upgradeToCity() = copy(upgradedToCity = true)
    override val participants: List<AbstractParty> get() = players

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is SettlementSchemaV1 -> SettlementSchemaV1.PersistentSettlementState(gameBoardLinearId.toString())
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        return listOf(SettlementSchemaV1)
    }
}

object SettlementSchema

@CordaSerializable
object SettlementSchemaV1 : MappedSchema(
        schemaFamily = SettlementSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentSettlementState::class.java)
) {
    @Entity
    @Table(
            name = "contract_settlement_states",
            indexes = [
                Index(name = "${BelongsToGameBoard.columnName}_idx", columnList = BelongsToGameBoard.columnName)
            ])
    class PersistentSettlementState(
            @Column(name = BelongsToGameBoard.columnName, nullable = false)
            var gameBoardLinearId: String
    ) : PersistentState(), StatePersistable
}
