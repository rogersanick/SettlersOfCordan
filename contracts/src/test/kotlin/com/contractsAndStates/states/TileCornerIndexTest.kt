package com.contractsAndStates.states

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TileCornerIndexTest {

    @Test
    fun `it rejects a negative index`() {
        assertFailsWith<IllegalArgumentException>() {
            TileCornerIndex(-1)
        }
    }

    @Test
    fun `it rejects a too large index`() {
        assertFailsWith<IllegalArgumentException>() {
            TileCornerIndex(HexTile.SIDE_COUNT)
        }
    }

    @Test
    fun `value is what you pass to the constructor`() {
        assertEquals(3, TileCornerIndex(3).value)
    }

    @Test
    fun `it gets the right next`() {
        assertEquals(TileCornerIndex(0), TileCornerIndex(5).next())
        assertEquals(TileCornerIndex(1), TileCornerIndex(0).next())
        assertEquals(TileCornerIndex(2), TileCornerIndex(1).next())
        assertEquals(TileCornerIndex(3), TileCornerIndex(2).next())
        assertEquals(TileCornerIndex(4), TileCornerIndex(3).next())
        assertEquals(TileCornerIndex(5), TileCornerIndex(4).next())
    }

    @Test
    fun `it gets the right previous`() {
        assertEquals(TileCornerIndex(0), TileCornerIndex(1).previous())
        assertEquals(TileCornerIndex(1), TileCornerIndex(2).previous())
        assertEquals(TileCornerIndex(2), TileCornerIndex(3).previous())
        assertEquals(TileCornerIndex(3), TileCornerIndex(4).previous())
        assertEquals(TileCornerIndex(4), TileCornerIndex(5).previous())
        assertEquals(TileCornerIndex(5), TileCornerIndex(0).previous())
    }

    @Test
    fun `it gets the right opposite`() {
        assertEquals(TileCornerIndex(0), TileCornerIndex(3).opposite())
        assertEquals(TileCornerIndex(1), TileCornerIndex(4).opposite())
        assertEquals(TileCornerIndex(2), TileCornerIndex(5).opposite())
        assertEquals(TileCornerIndex(3), TileCornerIndex(0).opposite())
        assertEquals(TileCornerIndex(4), TileCornerIndex(1).opposite())
        assertEquals(TileCornerIndex(5), TileCornerIndex(2).opposite())
    }

    @Test
    fun `it gets the right adjacent sides`() {
        assertEquals(
                listOf(TileSideIndex(0), TileSideIndex(1)),
                TileCornerIndex(1).getAdjacentSides())
        assertEquals(
                listOf(TileSideIndex(1), TileSideIndex(2)),
                TileCornerIndex(2).getAdjacentSides())
        assertEquals(
                listOf(TileSideIndex(2), TileSideIndex(3)),
                TileCornerIndex(3).getAdjacentSides())
        assertEquals(
                listOf(TileSideIndex(3), TileSideIndex(4)),
                TileCornerIndex(4).getAdjacentSides())
        assertEquals(
                listOf(TileSideIndex(4), TileSideIndex(5)),
                TileCornerIndex(5).getAdjacentSides())
        assertEquals(
                listOf(TileSideIndex(5), TileSideIndex(0)),
                TileCornerIndex(0).getAdjacentSides())
    }

    @Test
    fun `it gets the right next overlapped corner`() {
        assertEquals(
                TileCornerIndex(3),
                TileCornerIndex(1).getNextOverlappedCorner())
        assertEquals(
                TileCornerIndex(4),
                TileCornerIndex(2).getNextOverlappedCorner())
        assertEquals(
                TileCornerIndex(5),
                TileCornerIndex(3).getNextOverlappedCorner())
        assertEquals(
                TileCornerIndex(0),
                TileCornerIndex(4).getNextOverlappedCorner())
        assertEquals(
                TileCornerIndex(1),
                TileCornerIndex(5).getNextOverlappedCorner())
        assertEquals(
                TileCornerIndex(2),
                TileCornerIndex(0).getNextOverlappedCorner())
    }
}