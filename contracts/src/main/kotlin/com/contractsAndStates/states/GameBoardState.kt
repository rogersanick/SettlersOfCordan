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
data class GameBoardState(val hexTiles: PlacedHexTiles,
                          val ports: PlacedPorts,
                          val players: List<Party>,
                          val turnTrackerLinearId: UniqueIdentifier,
                          val robberLinearId: UniqueIdentifier,
                          val settlementsPlaced: PlacedSettlements = PlacedSettlements(),
                          val setUpComplete: Boolean = false,
                          val initialPiecesPlaced: Int = 0,
                          val winner: Party? = null,
                          val beginner: Boolean = false,
                          override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {

    override val participants: List<Party> get() = players

    companion object {
        const val TILE_COUNT = 19
    }

    fun weWin(ourIdentity: Party): GameBoardState {
        return this.copy(winner = ourIdentity)
    }

    /**
     * Returns the count of settlements built. It takes care not to include duplicates.
     */
    fun getSettlementsCount() = settlementsPlaced
            .allBuiltCorners()
            .distinctBy { hexTiles.getCornerHash(it) }
            .size
}

@CordaSerializable
data class PlacedSettlements @ConstructorForDeserialization constructor(
        val value: List<List<Boolean>> = List(GameBoardState.TILE_COUNT) {
            List(HexTile.SIDE_COUNT) { false }
        }) {

    init {
        require(value.size == GameBoardState.TILE_COUNT) {
            "value.size cannot be ${value.size}"
        }
        require(value.all { it.size == HexTile.SIDE_COUNT }) {
            "Each value list must be of ${HexTile.SIDE_COUNT} size"
        }
    }

    fun hasOn(corner: AbsoluteCorner) = hasOn(corner.tileIndex, corner.cornerIndex)
    fun hasOn(tileIndex: HexTileIndex, corner: TileCornerIndex) = value[tileIndex.value][corner.value]

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

    class Builder(private val value: List<MutableList<Boolean>> = List(GameBoardState.TILE_COUNT) {
        MutableList(HexTile.SIDE_COUNT) { false }
    }) {

        init {
            require(value.size == GameBoardState.TILE_COUNT) {
                "value.size cannot be ${value.size}"
            }
            require(value.all { it.size == HexTile.SIDE_COUNT }) {
                "Each value list must be of ${HexTile.SIDE_COUNT} size"
            }
        }

        fun hasOn(corner: AbsoluteCorner) = hasOn(corner.tileIndex, corner.cornerIndex)
        fun hasOn(tileIndex: HexTileIndex, corner: TileCornerIndex) = value[tileIndex.value][corner.value]
        fun placeOn(corner: AbsoluteCorner, sides: TileSides = TileSides()) =
                placeOn(corner.tileIndex, corner.cornerIndex, sides)

        fun placeOn(tileIndex: HexTileIndex, corner: TileCornerIndex, sides: TileSides = TileSides()): Builder = apply {
            require(!value[tileIndex.value][corner.value]) { "You cannot set a settlement twice" }
            value[tileIndex.value][corner.value] = true
            sides.getOverlappedCorners(corner).filterNotNull().forEach { if (!hasOn(it)) placeOn(it) }
        }

        fun build() = PlacedSettlements(value.map { ImmutableList(it) })
    }
}

internal class ImmutableList<T>(private val inner: List<T>) : List<T> by inner
