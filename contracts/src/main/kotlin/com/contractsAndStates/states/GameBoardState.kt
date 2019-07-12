package com.contractsAndStates.states

import com.contractsAndStates.contracts.GameStateContract
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.internal.toMultiMap
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
                          val ports: List<Port>,
                          val players: List<Party>,
                          val turnTrackerLinearId: UniqueIdentifier,
                          val robberLinearId: UniqueIdentifier,
                          val settlementsPlaced: List<List<Boolean>> = List(TILE_COUNT) { List(HexTile.SIDE_COUNT) { false } },
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
data class PlacedHexTiles(val value: List<HexTile>) {

    init {
        require(value.size == GameBoardState.TILE_COUNT) {
            "value.size cannot be ${value.size}"
        }
        val indices = value.map { it.hexTileIndex }
        require(indices.size == indices.toSet().size) {
            "There should be no duplicates in the indices list"
        }
        require(value.foldIndexed(true) { index, accum, tile ->
            accum && index == tile.hexTileIndex.value
        }) {
            "A hexTileIndex does not match its index in the value list"
        }
        require(value.filter { it.robberPresent }.singleOrNull() != null) {
            "A single title should have the robber"
        }
        val sortedResources = value
                .map { it.resourceType to it }
                .toMultiMap()
                .map { it.key to it.value.size }
                .toMap()
        require(sortedResources.keys == TILE_COUNT_PER_RESOURCE.keys) {
            "There is a mismatch between the resource types and the expected ones"
        }
        sortedResources.forEach { resource, count ->
            require(TILE_COUNT_PER_RESOURCE[resource] == count) {
                "There is a mismatch in the count of $resource tiles"
            }
        }
    }

    companion object {
        val rowIndices = listOf(
                0..2,
                3..6,
                7..11,
                12..15,
                16..18)
        val TILE_COUNT_PER_RESOURCE = mapOf(
                HexTileType.Desert to 1,
                HexTileType.Field to 4,
                HexTileType.Forest to 4,
                HexTileType.Hill to 3,
                HexTileType.Mountain to 3,
                HexTileType.Pasture to 4
        )
    }

    fun get(index: HexTileIndex) = value[index.value]
    fun indexOf(tile: HexTile?) = value.indexOf(tile)
    fun cloneList() = value.map { it }.toMutableList()

    class Builder(private val value: MutableList<HexTile.Builder> = mutableListOf()) {
        init {
            connectNeighbors()
        }

        fun connectNeighbors() {
            rowIndices.forEachIndexed { rowIndex, tileIndices ->
                val rowLength = tileIndices.count()
                tileIndices.forEachIndexed { index, tileIndex ->
                    // Last row has no neighbor below it
                    if (rowIndex < rowIndices.size - 1) {
                        // If your row is shorter than below, you have 2 neighbors below
                        if (rowLength < rowIndices[rowIndex + 1].count()) {
                            value[tileIndex].connect(TileSideIndex(3), value[tileIndex + rowLength])
                            value[tileIndex].connect(TileSideIndex(2), value[tileIndex + rowLength + 1])
                        }

                    }
                    // First row has no neighbor above it
                    if (0 < rowIndex) {
                        val aboveRowLength = rowIndices[rowIndex - 1].count()
                        // If your row is shorter than above, you have 2 neighbors above
                        if (rowLength < aboveRowLength) {
                            value[tileIndex].connect(TileSideIndex(5), value[tileIndex - aboveRowLength])
                            value[tileIndex].connect(TileSideIndex(0), value[tileIndex - aboveRowLength + 1])
                        }
                    }
                    // On each row, the last one has no neighbor to its right
                    if (index < rowLength - 1) {
                        value[tileIndex].connect(TileSideIndex(1), value[tileIndex + 1])
                    }
                }
            }
        }

        fun build() = PlacedHexTiles(ImmutableList(value.map { it.build() }))
    }
}

@CordaSerializable
data class HexTile(val resourceType: HexTileType,
                   val roleTrigger: Int,
                   val robberPresent: Boolean,
                   val hexTileIndex: HexTileIndex,
                   val sides: HexTileNeighbors = HexTileNeighbors(),
                   var roads: TileRoadIds = TileRoadIds()) {

    companion object {
        const val SIDE_COUNT = 6
    }

    /**
     * This method is used in flows to product a new version of the gameboard with a record of the location of roads, identified by
     * their specific linearID
     *
     * TODO: Add functionality to connect roadStates when new roads and proposed extending existing roads.
     */

    fun buildRoad(sideIndex: TileSideIndex, roadStateLinearId: UniqueIdentifier, hexTiles: PlacedHexTiles): PlacedHexTiles {

        val newMutableHexTiles = hexTiles.cloneList()

        val newMutableListOfRoads = newMutableHexTiles[this.hexTileIndex.value].roads.value.map { it }.toMutableList()
        newMutableListOfRoads.set(sideIndex.value, roadStateLinearId)

        val reciprocalSideIndex = sideIndex.opposite()
        val neighbor = this.sides.getNeighborOn(sideIndex)
        if (neighbor != null) {
            val newMutableReciprocalListOfRoads = newMutableHexTiles[neighbor.value].roads.value.map { it }.toMutableList()
            newMutableReciprocalListOfRoads.set(reciprocalSideIndex.value, roadStateLinearId)
            newMutableHexTiles[neighbor.value].roads = TileRoadIds(newMutableReciprocalListOfRoads)
        }

        newMutableHexTiles[this.hexTileIndex.value].roads = TileRoadIds(newMutableListOfRoads)
        return PlacedHexTiles(newMutableHexTiles)
    }

    class Builder {
        var resourceType: HexTileType? = null
            private set
        var roleTrigger: Int? = null
            private set
        var robberPresent: Boolean? = null
            private set
        var hexTileIndex: HexTileIndex? = null
            private set
        val sidesBuilder: HexTileNeighbors.Builder = HexTileNeighbors.Builder()
        var roads: TileRoadIds? = null
            private set

        fun with(resourceType: HexTileType) = apply { this.resourceType = resourceType }
        fun with(roleTrigger: Int) = apply { this.roleTrigger = roleTrigger }
        fun with(robberPresent: Boolean) = apply { this.robberPresent = robberPresent }
        fun with(hexTileIndex: HexTileIndex) = apply { this.hexTileIndex = hexTileIndex }
        fun with(roads: TileRoadIds) = apply { this.roads = roads }

        fun isConnectedWith(sideIndex: TileSideIndex, tileIndex: HexTileIndex) =
                sidesBuilder.getNeighborOn(sideIndex) == tileIndex

        /**
         * This method is used to create a fully connected graph of HexTiles. This enables some
         * funky maths that we will use later on to calculate the validity of transactions.
         */
        fun connect(mySideIndex: TileSideIndex, hexTileToConnect: HexTile.Builder): Builder = apply {
            val myTileIndex = hexTileIndex
            require(myTileIndex != null) { "You must set your hexTileIndex before you connect" }
            sidesBuilder.setNeighborOn(mySideIndex, hexTileToConnect.hexTileIndex)
            if (!hexTileToConnect.isConnectedWith(mySideIndex.opposite(), myTileIndex!!))
                hexTileToConnect.connect(mySideIndex.opposite(), this)
        }

        fun build() = HexTile(
                resourceType!!,
                roleTrigger!!,
                robberPresent!!,
                hexTileIndex!!,
                sidesBuilder.build(),
                roads!!
        )
    }
}

@CordaSerializable
data class TileRoadIds(val value: List<UniqueIdentifier?> = List(HexTile.SIDE_COUNT) { null }) {

    init {
        require(value.size == HexTile.SIDE_COUNT) { "roads.size cannot be ${value.size}" }
    }

    fun get(sideIndex: TileSideIndex) = value[sideIndex.value]
    fun hasOn(sideIndex: TileSideIndex) = get(sideIndex) != null
}

@CordaSerializable
data class PortTile(val inputRequired: List<Amount<TokenType>>, val outputRequired: List<Amount<TokenType>>) {

    init {
        val inputTypes = inputRequired.map { it.token }
        require(inputTypes.size == inputTypes.toSet().size) {
            "There should be no duplicates in the inputRequired list"
        }
        require(inputRequired.none { it.quantity == 0L }) {
            "No inputRequired should have a 0 quantity"
        }
        val outputTypes = outputRequired.map { it.token }
        require(outputTypes.size == outputTypes.toSet().size) {
            "There should be no duplicates in the outputRequired list"
        }
        require(outputRequired.none { it.quantity == 0L }) {
            "No outputRequired should have a 0 quantity"
        }
    }

    companion object {
        const val PORT_COUNT = 9
    }
}

@CordaSerializable
data class Port(val portTile: PortTile, var accessPoints: List<AccessPoint>)

@CordaSerializable
data class AccessPoint(val hexTileIndex: HexTileIndex, val hexTileCoordinate: List<TileCornerIndex>)

@CordaSerializable
data class HexTileNeighbors(val value: List<HexTileIndex?> = List(HexTile.SIDE_COUNT) { null }) {

    init {
        require(value.size == HexTile.SIDE_COUNT) { "sides.size cannot be ${value.size}" }
        val nonNull = value.filter { it != null }.map { it as HexTileIndex }
        require(nonNull.size == nonNull.toSet().size) {
            "There should be no non-null duplicates in the index list"
        }
    }

    fun getNeighborOn(sideIndex: TileSideIndex) = value[sideIndex.value]
    /**
     * The neighbors returned are ordered as per the argument.
     */
    fun getNeighborsOn(sideIndices: Iterable<TileSideIndex>) = sideIndices.map { getNeighborOn(it) }

    /**
     * The neighbors returned are ordered clockwise.
     */
    fun getNeighborsOn(cornerIndex: TileCornerIndex) = getNeighborsOn(cornerIndex.getAdjacentSides())

    fun hasNeighborOn(sideIndex: TileSideIndex) = getNeighborOn(sideIndex) != null
    fun indexOf(tileIndex: HexTileIndex) = TileSideIndex(value.indexOf(tileIndex))

    /**
     * The neighbor corners returned are ordered clockwise.
     */
    fun getOverlappedCorners(cornerIndex: TileCornerIndex) =
            cornerIndex.getNextOverlappedCorner().let {
                listOf(it, it.getNextOverlappedCorner())
            }.let { neighborCorners ->
                cornerIndex.getAdjacentSides()
                        .mapIndexed { index, it ->
                            val neighbor = getNeighborOn(it)
                            if (neighbor == null) null
                            else AbsoluteCorner(neighbor, neighborCorners[index])
                        }
            }

    class Builder {
        private val value: MutableList<HexTileIndex?> = MutableList(HexTile.SIDE_COUNT) { null }

        fun getNeighborOn(sideIndex: TileSideIndex) = value[sideIndex.value]
        fun setNeighborOn(sideIndex: TileSideIndex, neighbor: HexTileIndex?) = apply {
            value[sideIndex.value] = neighbor
        }

        fun build() = HexTileNeighbors(ImmutableList(value))
    }
}

data class AbsoluteCorner(val tileIndex: HexTileIndex, val cornerIndex: TileCornerIndex)

@CordaSerializable
data class HexTileIndex(val value: Int) {

    init {
        require(0 <= value && value < GameBoardState.TILE_COUNT) { "value cannot be $value" }
    }
}

@CordaSerializable
/**
 * Applies to roads.
 */
data class TileSideIndex(val value: Int) {

    init {
        require(0 <= value && value < HexTile.SIDE_COUNT) { "value cannot be $value" }
    }

    fun opposite() = TileSideIndex((value + HexTile.SIDE_COUNT / 2) % HexTile.SIDE_COUNT)
    /**
     * Goes counter-clockwise.
     */
    fun previous() = TileSideIndex((value + HexTile.SIDE_COUNT - 1) % HexTile.SIDE_COUNT)

    /**
     * Goes clockwise.
     */
    fun next() = TileSideIndex((value + 1) % HexTile.SIDE_COUNT)

    /**
     * The corners are ordered clockwise.
     */
    fun getAdjacentCorners() = TileCornerIndex(value).let { listOf(it, it.next()) }
}

@CordaSerializable
/**
 * Applies to settlements.
 */
data class TileCornerIndex(val value: Int) {

    init {
        require(0 <= value && value < HexTile.SIDE_COUNT) { "value cannot be $value" }
    }

    fun opposite() = TileCornerIndex((value + HexTile.SIDE_COUNT / 2) % HexTile.SIDE_COUNT)
    /**
     * Goes counter-clockwise.
     */
    fun previous() = TileCornerIndex((value + HexTile.SIDE_COUNT - 1) % HexTile.SIDE_COUNT)

    /**
     * Goes clockwise.
     */
    fun next() = TileCornerIndex((value + 1) % HexTile.SIDE_COUNT)

    /**
     * The sides are ordered clockwise.
     */
    fun getAdjacentSides() = TileSideIndex(value).let { listOf(it.previous(), it) }

    /**
     * The corner applies to the adjacent tile clockwise.
     */
    fun getNextOverlappedCorner() = TileCornerIndex((value + 2) % HexTile.SIDE_COUNT)
}

private class ImmutableList<T>(private val inner: List<T>) : List<T> by inner
