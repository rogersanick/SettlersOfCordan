package com.r3.cordan.primary.states.board

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.StatePersistable
import net.corda.core.serialization.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object GameBoardSchema

@CordaSerializable
object GameBoardSchemaV1 : MappedSchema(
        schemaFamily = GameBoardSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentGameBoardState::class.java)
) {
    @Entity
    @Table(name = "contract_game_board_states")
    class PersistentGameBoardState(
            @Column(name = "linear_id", nullable = false)
            var linearId: String
    ) : PersistentState(), StatePersistable
}