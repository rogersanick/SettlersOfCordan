package com.contractsAndStates.states

import com.contractsAndStates.contracts.GameStateContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
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
data class GameBoardState(
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        val hexTiles: PlacedHexTiles,
        val ports: PlacedPorts,
        val players: List<Party>,
        val turnTrackerLinearId: UniqueIdentifier,
        val robberLinearId: UniqueIdentifier,
        val settlementsPlaced: PlacedSettlements = PlacedSettlements(),
        val setUpComplete: Boolean = false,
        val initialPiecesPlaced: Int = 0,
        val winner: Party? = null,
        val beginner: Boolean = false
) : LinearState, TileLocator<HexTile> by hexTiles, AbsoluteSideLocator by hexTiles, AbsoluteCornerLocator by hexTiles,
        AbsoluteRoadLocator by hexTiles {

    override val participants: List<Party> get() = players

    companion object {
        const val TILE_COUNT = 19
    }

    fun weWin(ourIdentity: Party) = copy(winner = ourIdentity)

    /**
     * Returns the count of settlements built. It takes care not to include duplicates.
     */
    fun getSettlementsCount() = settlementsPlaced
            .allBuiltCorners()
            .distinctBy { hexTiles.getCornerHash(it) }
            .size

    /**
     * Also checks the overlapping corners.
     */
    fun hasSettlementOn(corner: AbsoluteCorner) = hexTiles.getOverlappedCorners(corner)
            .plus(corner)
            .filterNotNull()
            .any { settlementsPlaced.hasOn(it) }

    fun toBuilder() = Builder(
            linearId = linearId,
            hexTiles = hexTiles.toBuilder(),
            ports = ports.toBuilder(),
            players = players.toMutableList(),
            turnTrackerLinearId = turnTrackerLinearId,
            robberLinearId = robberLinearId,
            settlementsPlaced = settlementsPlaced.toBuilder(),
            setUpComplete = setUpComplete,
            initialPiecesPlaced = initialPiecesPlaced,
            winner = winner,
            beginner = beginner)

    class Builder(
            val linearId: UniqueIdentifier = UniqueIdentifier(),
            val hexTiles: PlacedHexTiles.Builder,
            val ports: PlacedPorts.Builder,
            private val players: MutableList<Party> = mutableListOf(),
            private var turnTrackerLinearId: UniqueIdentifier? = null,
            private var robberLinearId: UniqueIdentifier? = null,
            val settlementsPlaced: PlacedSettlements.Builder,
            private var setUpComplete: Boolean = false,
            private var initialPiecesPlaced: Int = 0,
            private var winner: Party? = null,
            private var beginner: Boolean = false
    ) : TileLocator<HexTile.Builder> by hexTiles, AbsoluteSideLocator by hexTiles, AbsoluteCornerLocator by hexTiles,
            AbsoluteRoadLocator by hexTiles, AbsoluteRoadBuilder by hexTiles {

        /**
         * Also checks the overlapping corners.
         */
        fun hasSettlementOn(corner: AbsoluteCorner) = hexTiles.getOverlappedCorners(corner)
                .plus(corner)
                .filterNotNull()
                .any { settlementsPlaced.hasOn(it) }

        fun placeSettlementOn(corner: AbsoluteCorner) = settlementsPlaced
                .placeOn(
                        corner,
                        hexTiles.get(corner.tileIndex).sidesBuilder)

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
                players = ImmutableList(players),
                turnTrackerLinearId = turnTrackerLinearId!!,
                robberLinearId = robberLinearId!!,
                settlementsPlaced = settlementsPlaced.build(),
                setUpComplete = setUpComplete,
                initialPiecesPlaced = initialPiecesPlaced,
                winner = winner,
                beginner = beginner
        )
    }
}

@CordaSerializable
data class PlacedSettlements @ConstructorForDeserialization constructor(
        val value: List<List<Boolean>> = List(GameBoardState.TILE_COUNT) {
            List(HexTile.SIDE_COUNT) { false }
        }) : SettlementLocator {

    init {
        require(value.size == GameBoardState.TILE_COUNT) {
            "value.size cannot be ${value.size}"
        }
        require(value.all { it.size == HexTile.SIDE_COUNT }) {
            "Each value list must be of ${HexTile.SIDE_COUNT} size"
        }
    }

    override fun hasOn(corner: AbsoluteCorner) = value[corner.tileIndex.value][corner.cornerIndex.value]

    /**
     * Returns all absolute corners on which there are settlements.
     * That includes duplicates, or triplicates, as there can be overlapping corners.
     */
    fun allBuiltCorners() = value
            .mapIndexed { tileIndex, corners ->
                corners.mapIndexedNotNull { cornerIndex, has ->
                    if (has) AbsoluteCorner(HexTileIndex(tileIndex), TileCornerIndex(cornerIndex))
                    else null
                }
            }
            .flatten()

    fun toBuilder() = Builder(value.map { it.toMutableList() })

    class Builder(private val value: List<MutableList<Boolean>> = List(GameBoardState.TILE_COUNT) {
        MutableList(HexTile.SIDE_COUNT) { false }
    }) : SettlementLocator, SettlementBuilder {

        init {
            require(value.size == GameBoardState.TILE_COUNT) {
                "value.size cannot be ${value.size}"
            }
            require(value.all { it.size == HexTile.SIDE_COUNT }) {
                "Each value list must be of ${HexTile.SIDE_COUNT} size"
            }
        }

        override fun hasOn(corner: AbsoluteCorner) = value[corner.tileIndex.value][corner.cornerIndex.value]
        override fun placeOn(corner: AbsoluteCorner, locator: CornerLocator?) =
                placeOn(corner.tileIndex, corner.cornerIndex, locator)

        override fun placeOn(tileIndex: HexTileIndex, corner: TileCornerIndex, locator: CornerLocator?): Builder = apply {
            require(!value[tileIndex.value][corner.value]) { "You cannot set a settlement twice" }
            value[tileIndex.value][corner.value] = true
            locator?.getOverlappedCorners(corner)?.filterNotNull()?.forEach { if (!hasOn(it)) placeOn(it) }
        }

        fun build() = PlacedSettlements(value.map { ImmutableList(it) })
    }
}

internal class ImmutableList<T>(private val inner: List<T>) : List<T> by inner

interface SettlementLocator {
    fun hasOn(corner: AbsoluteCorner): Boolean
    fun hasOn(tileIndex: HexTileIndex, corner: TileCornerIndex) = hasOn(AbsoluteCorner(tileIndex, corner))
}

interface SettlementBuilder {
    fun placeOn(corner: AbsoluteCorner, locator: CornerLocator? = null): SettlementBuilder
    fun placeOn(
            tileIndex: HexTileIndex,
            corner: TileCornerIndex,
            locator: CornerLocator? = null): SettlementBuilder
}