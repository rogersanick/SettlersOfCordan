package com.contractsAndStates.states

import com.contractsAndStates.contracts.TurnTrackerContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.schemas.StatePersistable
import net.corda.core.serialization.CordaSerializable
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

@CordaSerializable
@BelongsToContract(TurnTrackerContract::class)
data class TurnTrackerState(
        override val gameBoardLinearId: UniqueIdentifier,
        val currTurnIndex: Int = 0,
        val setUpRound1Complete: Boolean = false,
        val setUpRound2Complete: Boolean = false,
        override val participants: List<AbstractParty>,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState, QueryableState, StatePersistable, HasGameBoardId {
    fun endTurn() = copy(currTurnIndex = if (this.currTurnIndex + 1 < 4) this.currTurnIndex + 1 else 0, linearId = linearId)

    fun endTurnDuringInitialSettlementPlacement(): TurnTrackerState {
        if (setUpRound1Complete) {
            if (currTurnIndex == 0) return copy(currTurnIndex = currTurnIndex, setUpRound2Complete = true, linearId = linearId)
            return copy(currTurnIndex = currTurnIndex - 1, linearId = linearId)
        } else {
            if (currTurnIndex == 3) return copy(currTurnIndex = currTurnIndex, setUpRound1Complete = true)
            return copy(currTurnIndex = currTurnIndex + 1, linearId = linearId)
        }
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is TurnTrackerSchemaV1 -> TurnTrackerSchemaV1.PersistentTurnTrackerState(
                    gameBoardLinearId)
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        return listOf(TurnTrackerSchemaV1)
    }
}

object TurnTrackerSchema

@CordaSerializable
object TurnTrackerSchemaV1 : MappedSchema(
        schemaFamily = TurnTrackerSchema.javaClass,
        version = 1,
        mappedTypes = listOf(TurnTrackerState::class.java)
) {
    @Entity
    @Table(
            name = "contract_turn_tracker_states",
            indexes = [
                Index(name = "${BelongsToGameBoard.columnName}_idx", columnList = BelongsToGameBoard.columnName)
            ])
    class PersistentTurnTrackerState(
            gameBoardLinearId: UniqueIdentifier
    ) : BelongsToGameBoard(gameBoardLinearId)
}
