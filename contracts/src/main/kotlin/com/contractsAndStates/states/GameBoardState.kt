package com.contractsAndStates.states

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.GameStateContract
import com.oracleClientStatesAndContracts.states.RollTrigger
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.internal.toMultiMap
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

@CordaSerializable
data class PlacedHexTiles @ConstructorForDeserialization constructor(val value: List<HexTile>) {

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
            "A single tile should have the robber"
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
    fun indexOf(tile: HexTile?) = value.indexOf(tile).let {
        if (it < 0) null
        else HexTileIndex(it)
    }

    fun cloneList() = value.map { it }.toMutableList()
    fun toBuilder() = Builder(this)

    class Builder(private val value: MutableList<HexTile.Builder>) {

        constructor(placedHexTiles: PlacedHexTiles) :
                this(placedHexTiles.value.map { it.toBuilder() }.toMutableList())

        init {
            require(value.size == GameBoardState.TILE_COUNT) {
                "value.size cannot be ${value.size}"
            }
            connectNeighbors()
        }

        fun connectNeighbors() {
            rowIndices.forEachIndexed { rowIndex, columnIndices ->
                val rowLength = columnIndices.count()
                columnIndices.forEachIndexed { index, columnIndex ->
                    // Last row has no neighbor below it
                    if (rowIndex < rowIndices.size - 1) {
                        // If your row is shorter than below, you have 2 neighbors below
                        if (rowLength < rowIndices[rowIndex + 1].count()) {
                            value[columnIndex].connect(TileSideIndex(3), value[columnIndex + rowLength])
                            value[columnIndex].connect(TileSideIndex(2), value[columnIndex + rowLength + 1])
                        }

                    }
                    // First row has no neighbor above it
                    if (0 < rowIndex) {
                        val aboveRowLength = rowIndices[rowIndex - 1].count()
                        // If your row is shorter than above, you have 2 neighbors above
                        if (rowLength < aboveRowLength) {
                            value[columnIndex].connect(TileSideIndex(5), value[columnIndex - aboveRowLength])
                            value[columnIndex].connect(TileSideIndex(0), value[columnIndex - aboveRowLength + 1])
                        }
                    }
                    // On each row, the last one has no neighbor to its right
                    if (index < rowLength - 1) {
                        value[columnIndex].connect(TileSideIndex(1), value[columnIndex + 1])
                    }
                }
            }
        }

        /**
         * This method is used in flows to product a new version of the gameboard with a record of the location of roads, identified by
         * their specific linearID
         *
         * TODO: Add functionality to connect roadStates when new roads and proposed extending existing roads.
         */
        @Suspendable
        fun buildRoad(
                tileIndex: HexTileIndex,
                sideIndex: TileSideIndex,
                roadStateLinearId: UniqueIdentifier) = apply {
            value[tileIndex.value].sidesBuilder
                    .setRoadIdOn(sideIndex, roadStateLinearId)
                    .getNeighborOn(sideIndex)?.also { neighborIndex ->
                        value[neighborIndex.value].sidesBuilder
                                .setRoadIdOn(sideIndex.opposite(), roadStateLinearId)
                    }
        }

        fun build() = PlacedHexTiles(ImmutableList(value.map { it.build() }).toMutableList())
    }
}

@CordaSerializable
data class HexTile(
        val resourceType: HexTileType,
        val rollTrigger: RollTrigger,
        val robberPresent: Boolean,
        val hexTileIndex: HexTileIndex,
        val sides: TileSides = TileSides()) {

    companion object {
        const val SIDE_COUNT = 6
    }

    fun toBuilder() = Builder(this)

    class Builder(
            val hexTileIndex: HexTileIndex,
            resourceType: HexTileType? = null,
            rollTrigger: RollTrigger? = null,
            robberPresent: Boolean? = null,
            val sidesBuilder: TileSides.Builder = TileSides.Builder()) {

        constructor(tile: HexTile) : this(
                tile.hexTileIndex,
                tile.resourceType,
                tile.rollTrigger,
                tile.robberPresent,
                TileSides.Builder(tile.sides))

        var resourceType: HexTileType?
            private set
        var rollTrigger: RollTrigger?
            private set
        var robberPresent: Boolean?
            private set

        init {
            this.resourceType = resourceType
            this.rollTrigger = rollTrigger
            this.robberPresent = robberPresent
        }

        fun with(resourceType: HexTileType) = apply {
            require(this.resourceType.let { it == null || it == resourceType }) {
                "You cannot replace an existing resource type"
            }
            this.resourceType = resourceType
        }

        fun with(rollTrigger: RollTrigger) = apply {
            require(this.rollTrigger.let { it == null || it == rollTrigger }) {
                "You cannot replace an existing rollTrigger"
            }
            this.rollTrigger = rollTrigger
        }

        fun with(robberPresent: Boolean) = apply {
            require(this.robberPresent.let { it == null || it == robberPresent }) {
                "You cannot replace an existing robberPresent"
            }
            this.robberPresent = robberPresent
        }

        fun isConnectedWith(sideIndex: TileSideIndex, tileIndex: HexTileIndex) =
                sidesBuilder.getNeighborOn(sideIndex) == tileIndex

        /**
         * This method is used to create a fully connected graph of HexTiles. This enables some
         * funky maths that we will use later on to calculate the validity of transactions.
         */
        fun connect(mySideIndex: TileSideIndex, hexTileToConnect: Builder): Builder = apply {
            val myTileIndex = hexTileIndex
            sidesBuilder.setNeighborOn(mySideIndex, hexTileToConnect.hexTileIndex)
            if (!hexTileToConnect.isConnectedWith(mySideIndex.opposite(), myTileIndex))
                hexTileToConnect.connect(mySideIndex.opposite(), this)
        }

        fun build() = HexTile(
                resourceType!!,
                rollTrigger!!,
                robberPresent!!,
                hexTileIndex,
                sidesBuilder.build()
        )
    }
}

@CordaSerializable
data class PlacedPorts(val value: List<Port>) {

    init {
        require(value.size == PORT_COUNT) {
            "value.size cannot be ${value.size}"
        }
    }

    companion object {
        const val PORT_COUNT = 9
    }

    fun getPortAt(hexTile: HexTileIndex, tileCorner: TileCornerIndex) = value.single {
        it.accessPoints.any { accessPoint ->
            accessPoint.hexTileIndex == hexTile && accessPoint.hexTileCoordinate.contains(tileCorner)
        }
    }

    fun toBuilder() = Builder(
            value.map { it.portTile }.toMutableList(),
            value.map { it.accessPoints }.toMutableList())

    class Builder(
            val portTiles: MutableList<PortTile> = mutableListOf(),
            val accessPointsList: MutableList<List<AccessPoint>> = mutableListOf()) {

        companion object {
            fun createAllPorts() = Builder()
                    .add(PortTile(listOf(Sheep).mapOf(2), ALL_RESOURCES.minus(Sheep).mapOf(1)))
                    .add(PortTile(listOf(Wood).mapOf(2), ALL_RESOURCES.minus(Wood).mapOf(1)))
                    .add(PortTile(listOf(Brick).mapOf(2), ALL_RESOURCES.minus(Brick).mapOf(1)))
                    .add(PortTile(listOf(Ore).mapOf(2), ALL_RESOURCES.minus(Ore).mapOf(1)))
                    .add(PortTile(listOf(Wheat).mapOf(2), ALL_RESOURCES.minus(Wheat).mapOf(1)))
                    .add(PortTile(ALL_RESOURCES.mapOf(3), ALL_RESOURCES.mapOf(1)))
                    .add(PortTile(ALL_RESOURCES.mapOf(3), ALL_RESOURCES.mapOf(1)))
                    .add(PortTile(ALL_RESOURCES.mapOf(3), ALL_RESOURCES.mapOf(1)))
                    .add(PortTile(ALL_RESOURCES.mapOf(3), ALL_RESOURCES.mapOf(1)))
                    .add(listOf(AccessPoint(0, listOf(5, 1))))
                    .add(listOf(AccessPoint(1, listOf(0, 2)), AccessPoint(2, listOf(5))))
                    .add(listOf(AccessPoint(2, listOf(2)), AccessPoint(6, listOf(0, 1))))
                    .add(listOf(AccessPoint(11, listOf(1, 2))))
                    .add(listOf(AccessPoint(15, listOf(2, 3)), AccessPoint(18, listOf(1))))
                    .add(listOf(AccessPoint(18, listOf(4)), AccessPoint(17, listOf(2, 3))))
                    .add(listOf(AccessPoint(16, listOf(3, 4))))
                    .add(listOf(AccessPoint(12, listOf(4, 5)), AccessPoint(7, listOf(3))))
                    .add(listOf(AccessPoint(3, listOf(4, 5)), AccessPoint(7, listOf(0))))
        }

        fun add(portTile: PortTile) = apply { portTiles.add(portTile) }
        fun add(accessPoints: List<AccessPoint>) = apply {
            require(accessPoints.isNotEmpty()) { "accessPoints must not be empty" }
            accessPointsList.add(ImmutableList(accessPoints))
        }

        fun build(): PlacedPorts {
            require(portTiles.size == accessPointsList.size) {
                "ports and accessPointsList must have the same size"
            }
            return PlacedPorts(portTiles.mapIndexed { index, portTile ->
                Port(portTile, accessPointsList[index])
            })
        }
    }
}

@CordaSerializable
data class Port(val portTile: PortTile, val accessPoints: List<AccessPoint>) {

    init {
        require(accessPoints.isNotEmpty()) { "accessPoints must not be empty" }
    }
}

@CordaSerializable
data class PortTile(val inputRequired: List<Amount<TokenType>>, val outputRequired: List<Amount<TokenType>>) {

    init {
        require(inputRequired.isNotEmpty()) { "inputRequired must not be empty" }
        require(outputRequired.isNotEmpty()) { "outputRequired must not be empty" }
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

    fun getInputOf(token: TokenType) = inputRequired.single { it.token == token }
    fun getInputOf(resourceType: ResourceType) = getInputOf(Resource(resourceType))
    fun getOutputOf(token: TokenType) = outputRequired.single { it.token == token }
    fun getOutputOf(resourceType: ResourceType) = getOutputOf(Resource(resourceType))
}

@CordaSerializable
data class AccessPoint @ConstructorForDeserialization constructor(
        val hexTileIndex: HexTileIndex,
        val hexTileCoordinate: List<TileCornerIndex>) {

    constructor(hexTileIndex: Int, hexTileCoordinate: List<Int>) : this(
            HexTileIndex(hexTileIndex),
            hexTileCoordinate.map { TileCornerIndex(it) })

    init {
        require(hexTileCoordinate.isNotEmpty()) { "hexTileCoordinate must not be empty" }
    }
}

@CordaSerializable
data class TileSides @ConstructorForDeserialization constructor(
        val value: List<TileSide> = List(HexTile.SIDE_COUNT) { TileSide() }) {

    init {
        require(value.size == HexTile.SIDE_COUNT) { "value.size cannot be ${value.size}" }
        val nonNullNeighbors = value.filter { it.neighbor != null }
        require(nonNullNeighbors.size == nonNullNeighbors.toSet().size) {
            "There should be no non-null duplicates in the neighbors list"
        }
        val nonNullRoads = value.filter { it.roadId != null }
        require(nonNullRoads.size == nonNullRoads.toSet().size) {
            "There should be no non-null duplicates in the roadIds list"
        }
    }

    fun getSideOn(sideIndex: TileSideIndex) = value[sideIndex.value]
    fun indexOf(tileSide: TileSide) = value.indexOf(tileSide).let {
        if (it < 0) null
        else TileSideIndex(it)
    }

    fun getNeighborOn(sideIndex: TileSideIndex) = getSideOn(sideIndex).neighbor
    /**
     * The neighbors returned are ordered as per the argument.
     */
    fun getNeighborsOn(sideIndices: Iterable<TileSideIndex>) = sideIndices.map { getNeighborOn(it) }

    /**
     * The neighbors returned are ordered clockwise.
     */
    fun getNeighborsOn(cornerIndex: TileCornerIndex) = getNeighborsOn(cornerIndex.getAdjacentSides())

    fun hasNeighborOn(sideIndex: TileSideIndex) = getNeighborOn(sideIndex) != null
    fun indexOf(tileIndex: HexTileIndex) = value.map { it.neighbor }.indexOf(tileIndex).let {
        if (it < 0) null
        else TileSideIndex(it)
    }

    fun getRoadIdOn(sideIndex: TileSideIndex) = getSideOn(sideIndex).roadId
    fun hasRoadIdOn(sideIndex: TileSideIndex) = getRoadIdOn(sideIndex) != null
    fun indexOf(roadId: UniqueIdentifier) = value.map { it.roadId }.indexOf(roadId).let {
        if (it < 0) null
        else TileSideIndex(it)
    }

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

    class Builder(
            private val value: MutableList<TileSide> = MutableList(HexTile.SIDE_COUNT) { TileSide() }) {

        constructor(tileSides: TileSides) : this(tileSides.value.toMutableList())

        fun getSideOn(sideIndex: TileSideIndex) = value[sideIndex.value]
        fun setSideOn(sideIndex: TileSideIndex, tileSide: TileSide) = apply {
            require(getNeighborOn(sideIndex).let { it == null || it == tileSide.neighbor }) {
                "You cannot replace an existing neighbor"
            }
            require(getRoadIdOn(sideIndex).let { it == null || it == tileSide.roadId }) {
                "You cannot build a new road on top of a current one"
            }
            value[sideIndex.value] = tileSide
        }

        fun getNeighborOn(sideIndex: TileSideIndex) = getSideOn(sideIndex).neighbor
        fun setNeighborOn(sideIndex: TileSideIndex, neighbor: HexTileIndex) = apply {
            require(getNeighborOn(sideIndex).let { it == null || it == neighbor }) {
                "You cannot replace an existing neighbor"
            }
            setSideOn(sideIndex, getSideOn(sideIndex).copy(neighbor = neighbor))
        }

        fun getRoadIdOn(sideIndex: TileSideIndex) = getSideOn(sideIndex).roadId
        fun setRoadIdOn(sideIndex: TileSideIndex, roadId: UniqueIdentifier) = apply {
            require(getRoadIdOn(sideIndex).let { it == null || it == roadId }) {
                "You cannot build a new road on top of a current one"
            }
            setSideOn(sideIndex, getSideOn(sideIndex).copy(roadId = roadId))
        }

        fun build() = TileSides(ImmutableList(value))
    }
}

@CordaSerializable
data class AbsoluteCorner(val tileIndex: HexTileIndex, val cornerIndex: TileCornerIndex)

@CordaSerializable
data class HexTileIndex(val value: Int) {

    init {
        require(0 <= value && value < GameBoardState.TILE_COUNT) { "Hex tile index value cannot be $value" }
    }
}

@CordaSerializable
data class TileSide(val neighbor: HexTileIndex? = null, val roadId: UniqueIdentifier? = null)

@CordaSerializable
/**
 * Applies to roads.
 */
data class TileSideIndex(val value: Int) {

    init {
        require(0 <= value && value < HexTile.SIDE_COUNT) { "Hex tile side index value cannot be $value" }
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
        require(0 <= value && value < HexTile.SIDE_COUNT) { "Hex tile corner index value cannot be $value" }
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

    /**
     * The corners of the adjacent tiles clockwise.
     */
    fun getOverlappedCorners() = getNextOverlappedCorner().let {
        listOf(it, it.getNextOverlappedCorner())
    }
}

private class ImmutableList<T>(private val inner: List<T>) : List<T> by inner
