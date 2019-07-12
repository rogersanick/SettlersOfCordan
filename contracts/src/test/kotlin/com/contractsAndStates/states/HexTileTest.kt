package com.contractsAndStates.states

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HexTileTest {

    private fun List<Int?>.makeNeighbors() = map {
        if (it == null) null
        else HexTileIndex(it)
    }

    private fun List<Int?>.makeBuilder() = HexTileNeighbors.Builder()
            .also { builder ->
                forEachIndexed { index, it ->
                    if (it != null) builder.setNeighborOn(TileSideIndex(index), HexTileIndex(it))
                }
            }

    @Test
    fun `it rejects a too short list`() {
        assertFailsWith<IllegalArgumentException>() {
            HexTileNeighbors(List(HexTile.SIDE_COUNT - 1) { null })
        }
    }

    @Test
    fun `it rejects a too large index`() {
        assertFailsWith<IllegalArgumentException>() {
            HexTileNeighbors(List(HexTile.SIDE_COUNT + 1) { null })
        }
    }

    @Test
    fun `it rejects when there is a duplicate index`() {
        assertFailsWith<IllegalArgumentException>() {
            HexTileNeighbors(listOf(2, null, null, 0, 2, 12).makeNeighbors())
        }
    }

    @Test
    fun `value is what you pass to the constructor`() {
        assertEquals(
                listOf(2, null, null, 0, 5, 12).makeNeighbors(),
                HexTileNeighbors(listOf(2, null, null, 0, 5, 12).makeNeighbors()).value)
        assertEquals(
                listOf(2, 4, 1, 0, 5, 12).makeNeighbors(),
                HexTileNeighbors(listOf(2, 4, 1, 0, 5, 12).makeNeighbors()).value)
    }

    @Test
    fun `Builder-getNeighborOn is correct`() {
        val builder = listOf(2, null, null, 0, 5, 12).makeBuilder()
        assertEquals(HexTileIndex(2), builder.getNeighborOn(TileSideIndex(0)))
        assertEquals(null, builder.getNeighborOn(TileSideIndex(1)))
        assertEquals(null, builder.getNeighborOn(TileSideIndex(2)))
        assertEquals(HexTileIndex(0), builder.getNeighborOn(TileSideIndex(3)))
        assertEquals(HexTileIndex(5), builder.getNeighborOn(TileSideIndex(4)))
        assertEquals(HexTileIndex(12), builder.getNeighborOn(TileSideIndex(5)))
    }

    @Test
    fun `Builder-build is correct`() {
        val built = listOf(2, null, null, 0, 5, 12).makeBuilder().build()
        val directly = HexTileNeighbors(listOf(2, null, null, 0, 5, 12).makeNeighbors())

        built.value.forEachIndexed { index, it ->
            assertEquals(directly.value[index], it)
        }
    }

    @Test
    fun `getNeighborOn is correct`() {
        val neighbors = HexTileNeighbors(listOf(2, null, null, 0, 5, 12).makeNeighbors())
        assertEquals(HexTileIndex(2), neighbors.getNeighborOn(TileSideIndex(0)))
        assertEquals(null, neighbors.getNeighborOn(TileSideIndex(1)))
        assertEquals(null, neighbors.getNeighborOn(TileSideIndex(2)))
        assertEquals(HexTileIndex(0), neighbors.getNeighborOn(TileSideIndex(3)))
        assertEquals(HexTileIndex(5), neighbors.getNeighborOn(TileSideIndex(4)))
        assertEquals(HexTileIndex(12), neighbors.getNeighborOn(TileSideIndex(5)))
    }

    @Test
    fun `hasNeighborOn is correct`() {
        val neighbors = HexTileNeighbors(listOf(2, null, null, 0, 5, 12).makeNeighbors())
        assertTrue(neighbors.hasNeighborOn(TileSideIndex(0)))
        assertFalse(neighbors.hasNeighborOn(TileSideIndex(1)))
        assertFalse(neighbors.hasNeighborOn(TileSideIndex(2)))
        assertTrue(neighbors.hasNeighborOn(TileSideIndex(3)))
        assertTrue(neighbors.hasNeighborOn(TileSideIndex(4)))
        assertTrue(neighbors.hasNeighborOn(TileSideIndex(5)))
    }

    @Test
    fun `getNeighborsOn by corner is correct`() {
        val neighbors = HexTileNeighbors(listOf(2, null, null, 0, 5, 12).makeNeighbors())
        assertEquals(
                listOf(HexTileIndex(12), HexTileIndex(2)),
                neighbors.getNeighborsOn(TileCornerIndex(0)))
        assertEquals(
                listOf(HexTileIndex(2), null),
                neighbors.getNeighborsOn(TileCornerIndex(1)))
        assertEquals(
                listOf(null, null),
                neighbors.getNeighborsOn(TileCornerIndex(2)))
        assertEquals(
                listOf(null, HexTileIndex(0)),
                neighbors.getNeighborsOn(TileCornerIndex(3)))
        assertEquals(
                listOf(HexTileIndex(0), HexTileIndex(5)),
                neighbors.getNeighborsOn(TileCornerIndex(4)))
        assertEquals(
                listOf(HexTileIndex(5), HexTileIndex(12)),
                neighbors.getNeighborsOn(TileCornerIndex(5)))
    }

    @Test
    fun `indexOf by tile index is correct`() {
        val neighbors = HexTileNeighbors(listOf(2, null, null, 0, 5, 12).makeNeighbors())
        assertEquals(TileSideIndex(0), neighbors.indexOf(HexTileIndex(2)))
        assertEquals(TileSideIndex(3), neighbors.indexOf(HexTileIndex(0)))
        assertEquals(TileSideIndex(4), neighbors.indexOf(HexTileIndex(5)))
        assertEquals(TileSideIndex(5), neighbors.indexOf(HexTileIndex(12)))
    }

    @Test
    fun `getOverlappedCorners is correct`() {
        val neighbors = HexTileNeighbors(listOf(2, null, null, 0, 5, 12).makeNeighbors())
        assertEquals(
                listOf(
                        AbsoluteCorner(HexTileIndex(12), TileCornerIndex(2)),
                        AbsoluteCorner(HexTileIndex(2), TileCornerIndex(4))
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(0))
        )
        assertEquals(
                listOf(
                        AbsoluteCorner(HexTileIndex(2), TileCornerIndex(3)),
                        null
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(1))
        )
        assertEquals(
                listOf(
                        null,
                        null
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(2))
        )
        assertEquals(
                listOf(
                        null,
                        AbsoluteCorner(HexTileIndex(0), TileCornerIndex(1))
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(3))
        )
        assertEquals(
                listOf(
                        AbsoluteCorner(HexTileIndex(0), TileCornerIndex(0)),
                        AbsoluteCorner(HexTileIndex(5), TileCornerIndex(2))
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(4))
        )
        assertEquals(
                listOf(
                        AbsoluteCorner(HexTileIndex(5), TileCornerIndex(1)),
                        AbsoluteCorner(HexTileIndex(12), TileCornerIndex(3))
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(5))
        )
    }
}