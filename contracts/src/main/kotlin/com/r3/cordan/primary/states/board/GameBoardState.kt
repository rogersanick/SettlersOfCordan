package com.r3.cordan.primary.states.board

import com.r3.cordan.primary.contracts.board.GameStateContract
import com.r3.cordan.primary.states.robber.RobberState
import com.r3.cordan.primary.states.trade.TradeState
import com.r3.cordan.primary.states.turn.TurnTrackerState
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.schemas.StatePersistable
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.CordaSerializable

/**
 * This state represents the same shared data that the symbolic representation of the Settlers board game
 * and game-pieces represent in real life.
 *
 * It contains an ordered list of the hexTiles that were used in its construction, the locations and
 * specifications of individual ports, and other pieces of data that act as infrastructure and validation for
 * future transactions. It is frequently used as a reference state (for example, in the issueResourcesFlow)
 */

@CordaSerializable
@BelongsToContract(GameStateContract::class)
data class GameBoardState @ConstructorForDeserialization constructor(
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        val hexTiles: PlacedHexTiles,
        val ports: PlacedPorts,
        val players: MutableList<Party>,
        val turnTrackerLinearId: UniqueIdentifier,
        val robberLinearId: UniqueIdentifier,
        val setUpComplete: Boolean = false,
        val initialPiecesPlaced: Int = 0,
        val winner: Party? = null,
        val beginner: Boolean = false
) : QueryableState, LinearState,
        TileLocator<HexTile> by hexTiles,
        AbsoluteSideLocator by hexTiles,
        AbsoluteCornerLocator by hexTiles,
        AbsoluteRoadLocator by hexTiles,
        AbsoluteSettlementLocator by hexTiles {

    override val participants: List<Party> get() = players

    companion object {
        const val TILE_COUNT = 19
    }

    fun weWin(ourIdentity: Party) = copy(winner = ourIdentity)

    fun isValid(turnTrackerState: TurnTrackerState) = turnTrackerState.linearId == turnTrackerLinearId &&
            turnTrackerState.gameBoardLinearId == linearId

    fun isValid(robberState: RobberState) = robberState.linearId == robberLinearId &&
            robberState.gameBoardLinearId == linearId

    fun isValid(tradeState: TradeState) = tradeState.gameBoardLinearId == linearId

    fun toBuilder() = Builder(
            linearId = linearId,
            hexTiles = hexTiles.toBuilder(),
            ports = ports.toBuilder(),
            players = players.toMutableList(),
            turnTrackerLinearId = turnTrackerLinearId,
            robberLinearId = robberLinearId,
            setUpComplete = setUpComplete,
            initialPiecesPlaced = initialPiecesPlaced,
            winner = winner,
            beginner = beginner)

    fun playerKeys() = players.map { it.owningKey }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is GameBoardSchemaV1 -> GameBoardSchemaV1.PersistentGameBoardState(linearId.toString())
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        return listOf(GameBoardSchemaV1)
    }

    class Builder(
            val linearId: UniqueIdentifier = UniqueIdentifier(),
            val hexTiles: PlacedHexTiles.Builder,
            val ports: PlacedPorts.Builder,
            private val players: MutableList<Party> = mutableListOf(),
            private var turnTrackerLinearId: UniqueIdentifier? = null,
            private var robberLinearId: UniqueIdentifier? = null,
            private var setUpComplete: Boolean = false,
            private var initialPiecesPlaced: Int = 0,
            private var winner: Party? = null,
            private var beginner: Boolean = false
    ) : TileLocator<HexTile.Builder> by hexTiles,
            AbsoluteSideLocator by hexTiles,
            AbsoluteCornerLocator by hexTiles,
            AbsoluteRoadLocator by hexTiles,
            AbsoluteRoadBuilder by hexTiles,
            AbsoluteSettlementLocator by hexTiles,
            AbsoluteSettlementBuilder by hexTiles {

        companion object {
            fun createFull() = Builder(
                    hexTiles = PlacedHexTiles.Builder.createFull(),
                    ports = PlacedPorts.Builder.createAllPorts()
            )
        }

        fun addPlayers(newPlayers: List<Party>) = apply { players.addAll(newPlayers) }
        fun withTurnTracker(id: UniqueIdentifier) = apply {
            require(turnTrackerLinearId == null || turnTrackerLinearId == id) {
                "You cannot overwrite the turnTrackerLinearId"
            }
            turnTrackerLinearId = id
        }

        fun withRobber(id: UniqueIdentifier) = apply {
            require(robberLinearId == null || robberLinearId == id) {
                "You cannot overwrite the robberLinearId"
            }
            robberLinearId = id
        }

        fun setSetupComplete() = apply { setUpComplete = true }
        fun addInitialPiecesPlaced(extra: Int) = apply { initialPiecesPlaced += extra }
        fun withWinner(newWinner: Party) = apply {
            require(winner == null || winner == newWinner) { "You cannot overwrite the winner" }
            winner = newWinner
        }

        fun setBeginner() = apply { beginner = true }

        fun build() = GameBoardState(
                linearId = linearId,
                hexTiles = hexTiles.build(),
                ports = ports.build(),
                players = players,
                turnTrackerLinearId = turnTrackerLinearId!!,
                robberLinearId = robberLinearId!!,
                setUpComplete = setUpComplete,
                initialPiecesPlaced = initialPiecesPlaced,
                winner = winner,
                beginner = beginner
        )
    }
}

internal class ImmutableList<T>(private val inner: List<T>) : List<T> by inner

interface HasGameBoardId {
    val gameBoardLinearId: UniqueIdentifier
}

interface HasGameBoardIdSchema {
    val gameBoardLinearId: String
}

/**
 * Other persistence classes cannot inherit this class. Otherwise the system cannot detect the column name.
 */
sealed class BelongsToGameBoard {
    companion object {
        const val columnName = "game_board_linear_id"
    }
}
