package com.contractsAndStates.states

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.internal.toMultiMap
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.CordaSerializable

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

    fun getOpposite(absoluteSide: AbsoluteSide) = get(absoluteSide.tileIndex)
            .sides
            .getNeighborOn(absoluteSide.sideIndex)
            .let { AbsoluteSide.create(it, absoluteSide.sideIndex.opposite()) }

    /**
     * When 1 or 2 AbsoluteSides are neighbors, the smaller equivalent is the one with the lower tileIndex.
     */
    fun getSmallerEquivalent(side: AbsoluteSide) = getOpposite(side).let { opposite ->
        if (opposite == null || side.tileIndex.value <= opposite.tileIndex.value) side
        else opposite
    }

    fun getSideHash(side: AbsoluteSide) = getSmallerEquivalent(side).let {
        it.tileIndex.value * HexTile.SIDE_COUNT + it.sideIndex.value
    }

    /**
     * When opposite sides, then the one with the lower tile index is smaller.
     * When different sides, then, with the opposites, the one with the lower tile index is smaller,
     * and if equal, the one with the smaller side index is smaller.
     */
    fun getSideComparator() = Comparator<AbsoluteSide> { left, right ->
        if (left == null && right == null) return@Comparator 0
        if (left == null) return@Comparator -1
        if (right == null) return@Comparator 1
        val leftEq = getSmallerEquivalent(left)
        val rightEq = getSmallerEquivalent(right)
        if (left == right) 0
        else if (leftEq == rightEq) left.tileIndex.value - right.tileIndex.value
        else (leftEq.tileIndex.value - rightEq.tileIndex.value).let {
            if (it != 0) it
            else leftEq.sideIndex.value - rightEq.sideIndex.value
        }
    }

    fun getOverlappedCorners(absoluteCorner: AbsoluteCorner) = get(absoluteCorner.tileIndex)
            .sides
            .getNeighborsOn(absoluteCorner.cornerIndex)
            .zip(absoluteCorner.cornerIndex.getOverlappedCorners())
            .map { AbsoluteCorner.create(it.first, it.second) }

    /**
     * When 1, 2 or 3 AbsoluteCorners are neighbors, the smaller equivalent is the one with the lower tileIndex.
     */
    fun getSmallestEquivalent(corner: AbsoluteCorner) = getOverlappedCorners(corner)
            .fold(corner) { choice, next ->
                if (next == null || choice.tileIndex.value <= next.tileIndex.value) choice
                else next
            }

    fun getCornerHash(corner: AbsoluteCorner) = getSmallestEquivalent(corner).let {
        it.tileIndex.value * HexTile.SIDE_COUNT + it.cornerIndex.value
    }

    /**
     * When overlapping corner, then they are ordered by their tile index.
     * When different corners, then, they are ordered by the tile order of their smallest overlapping ones,
     * and if equal, the one with the smaller side index is smaller.
     */
    fun getCornerComparator() = Comparator<AbsoluteCorner> { left, right ->
        if (left == null && right == null) return@Comparator 0
        if (left == null) return@Comparator -1
        if (right == null) return@Comparator 1
        val leftEq = getSmallestEquivalent(left)
        val rightEq = getSmallestEquivalent(right)
        if (left == right) 0
        else if (leftEq == rightEq) left.tileIndex.value - right.tileIndex.value
        else (leftEq.tileIndex.value - rightEq.tileIndex.value).let {
            if (it != 0) it
            else leftEq.cornerIndex.value - rightEq.cornerIndex.value
        }
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
