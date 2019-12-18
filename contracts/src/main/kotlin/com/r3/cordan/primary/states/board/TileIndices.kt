package com.r3.cordan.primary.states.board

import com.r3.cordan.primary.states.structure.GameBoardState
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class AbsoluteSide(val tileIndex: HexTileIndex, val sideIndex: TileSideIndex) {

    companion object {
        fun create(tileIndex: HexTileIndex?, sideIndex: TileSideIndex) =
                if (tileIndex == null) null
                else AbsoluteSide(tileIndex, sideIndex)
    }

    /**
     * Goes counter-clockwise.
     */
    fun previous() = copy(sideIndex = sideIndex.previous())

    /**
     * Goes clockwise.
     */
    fun next() = copy(sideIndex = sideIndex.next())

    fun getAdjacentCorners() = sideIndex
            .getAdjacentCorners()
            .map { AbsoluteCorner(tileIndex, it) }
}

@CordaSerializable
data class AbsoluteCorner @ConstructorForDeserialization constructor(
        val tileIndex: HexTileIndex,
        val cornerIndex: TileCornerIndex) {

    companion object {
        fun create(tileIndex: HexTileIndex?, cornerIndex: TileCornerIndex) =
                if (tileIndex == null) null
                else AbsoluteCorner(tileIndex, cornerIndex)
    }

    /**
     * Goes counter-clockwise.
     */
    fun previous() = copy(cornerIndex = cornerIndex.previous())

    /**
     * Goes clockwise.
     */
    fun next() = copy(cornerIndex = cornerIndex.next())

    fun getAdjacentSides() = cornerIndex
            .getAdjacentSides()
            .map { AbsoluteSide(tileIndex, it) }

}

@CordaSerializable
data class HexTileIndex(val value: Int) {

    init {
        require(0 <= value && value < GameBoardState.TILE_COUNT) { "Hex tile index value cannot be $value" }
    }
}

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
