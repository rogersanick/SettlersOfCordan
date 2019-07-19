package com.contractsAndStates.states

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.CordaSerializable

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
    fun getOverlappedCorners(cornerIndex: TileCornerIndex) = getNeighborsOn(cornerIndex)
            .zip(cornerIndex.getOverlappedCorners())
            .map { pair ->
                pair.first.let {
                    if (it == null) null
                    else AbsoluteCorner(it, pair.second)
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

        fun build() = TileSides(ImmutableList(value).toMutableList())
    }
}
