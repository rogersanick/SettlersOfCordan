package com.contractsAndStates.states

import com.contractsAndStates.contracts.GameStateContract
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
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
data class GameBoardState(val hexTiles: MutableList<HexTile>,
                          val ports: List<Port>,
                          val players: List<Party>,
                          val turnTrackerLinearId: UniqueIdentifier,
                          val robberLinearId: UniqueIdentifier,
                          val settlementsPlaced: MutableList<MutableList<Boolean>> = MutableList(TILE_COUNT) { MutableList(HexTile.SIDE_COUNT) { false } },
                          val setUpComplete: Boolean = false,
                          val initialPiecesPlaced: Int = 0,
                          val winner: Party? = null,
                          val beginner: Boolean = false,
                          override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {

    init {
        require(settlementsPlaced.size == TILE_COUNT) {
            "settlementsPlaced.size cannot be ${settlementsPlaced.size}"
        }
        require(settlementsPlaced.all { it.size == HexTile.SIDE_COUNT }) {
            "Each settlementsPlaced list must be of ${HexTile.SIDE_COUNT}"
        }
    }

    override val participants: List<Party> get() = players

    companion object {
        const val TILE_COUNT = 19
    }

    fun weWin(ourIdentity: Party): GameBoardState {
        return this.copy(winner = ourIdentity)
    }

}

@CordaSerializable
class HexTile(val resourceType: String,
              val roleTrigger: Int,
              val robberPresent: Boolean,
              val hexTileIndex: HexTileIndex,
              var sides: MutableList<HexTileIndex?> = MutableList(SIDE_COUNT) { null },
              var roads: MutableList<UniqueIdentifier?> = MutableList(SIDE_COUNT) { null }) {

    init {
        require(sides.size == SIDE_COUNT) { "sides.size cannot be ${sides.size}" }
        require(roads.size == SIDE_COUNT) { "roads.size cannot be ${roads.size}" }
    }

    companion object {
        const val SIDE_COUNT = 6
    }

    /**
     * This method is used to create a fully connected graph of HexTiles. This enables some
     * funky maths that we will use later on to calculate the validity of transactions.
     */

    fun connect(mySideIndex: TileSideIndex, hexTileToConnect: HexTile) {
        sides[mySideIndex.value] = hexTileToConnect.hexTileIndex
        hexTileToConnect.sides[mySideIndex.opposite().value] = hexTileIndex
    }

    /**
     * This method is used in flows to product a new version of the gameboard with a record of the location of roads, identified by
     * their specific linearID
     *
     * TODO: Add functionality to connect roadStates when new roads and proposed extending existing roads.
     */

    fun buildRoad(sideIndex: TileSideIndex, roadStateLinearId: UniqueIdentifier, hexTiles: MutableList<HexTile>): MutableList<HexTile> {

        var newMutableHexTiles = hexTiles.map { it }.toMutableList()

        var newMutableListOfRoads = newMutableHexTiles[this.hexTileIndex.value].roads.map { it }.toMutableList()
        newMutableListOfRoads.set(sideIndex.value, roadStateLinearId)

        val reciprocalSideIndex = sideIndex.opposite()
        if (this.sides[sideIndex.value] != null) {
            var newMutableReciprocalListOfRoads = newMutableHexTiles[this.sides[sideIndex.value]!!.value].roads.map { it }.toMutableList()
            newMutableReciprocalListOfRoads.set(reciprocalSideIndex.value, roadStateLinearId)
            newMutableHexTiles[this.sides[sideIndex.value]!!.value].roads = newMutableReciprocalListOfRoads
        }

        newMutableHexTiles[this.hexTileIndex.value].roads = newMutableListOfRoads
        return newMutableHexTiles
    }

}

@CordaSerializable
data class PortTile(val inputRequired: List<Amount<TokenType>>, val outputRequired: List<Amount<TokenType>>)

@CordaSerializable
data class Port(val portTile: PortTile, var accessPoints: List<AccessPoint>)

@CordaSerializable
data class AccessPoint(val hexTileIndex: HexTileIndex, val hexTileCoordinate: List<Int>)

data class HexTileIndex(val value: Int) {

    init {
        require(0 <= value && value < GameBoardState.TILE_COUNT) { "value cannot be $value" }
    }
}

/**
 * Applies to roads.
 */
data class TileSideIndex(val value: Int) {

    init {
        require(0 <= value && value < HexTile.SIDE_COUNT) { "value cannot be $value" }
    }

    // TODO does + 3 % 5 work?
    fun opposite() = TileSideIndex(
            if (value + HexTile.SIDE_COUNT / 2 < HexTile.SIDE_COUNT) value + HexTile.SIDE_COUNT / 2
            else value - HexTile.SIDE_COUNT / 2)

    fun previous() = TileSideIndex(
            if (value - 1 < 0) HexTile.SIDE_COUNT - 1
            else value - 1)

    fun next() = TileSideIndex(
            if (value + 1 < HexTile.SIDE_COUNT) value + 1
            else 0)
}

/**
 * Applies to settlements.
 * TODO Confirm that corner 1 is between side 0 and side 1.
 */
data class TileCornerIndex(val value: Int) {

    init {
        require(0 <= value && value < HexTile.SIDE_COUNT) { "value cannot be $value" }
    }

    // TODO does + 3 % 5 work?
    fun opposite() = TileCornerIndex(
            if (value + HexTile.SIDE_COUNT / 2 < HexTile.SIDE_COUNT) value + HexTile.SIDE_COUNT / 2
            else value - HexTile.SIDE_COUNT / 2)

    fun previous() = TileCornerIndex(
            if (value < 1) HexTile.SIDE_COUNT - 1
            else value - 1)

    fun next() = TileCornerIndex(
            if (value + 1 < HexTile.SIDE_COUNT) value + 1
            else 0)
}
