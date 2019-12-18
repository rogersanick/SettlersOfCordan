package com.r3.cordan.primary.states

import com.r3.cordan.primary.states.board.HexTile
import com.r3.cordan.primary.states.board.TileCornerIndex
import com.r3.cordan.primary.states.board.TileSideIndex
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TileSideIndexTest {

    @Test
    fun `it rejects a negative index`() {
        assertFailsWith<IllegalArgumentException>() {
            TileSideIndex(-1)
        }
    }

    @Test
    fun `it rejects a too large index`() {
        assertFailsWith<IllegalArgumentException>() {
            TileSideIndex(HexTile.SIDE_COUNT)
        }
    }

    @Test
    fun `value is what you pass to the constructor`() {
        assertEquals(3, TileSideIndex(3).value)
    }

    @Test
    fun `it gets the right next`() {
        assertEquals(TileSideIndex(0), TileSideIndex(5).next())
        assertEquals(TileSideIndex(1), TileSideIndex(0).next())
        assertEquals(TileSideIndex(2), TileSideIndex(1).next())
        assertEquals(TileSideIndex(3), TileSideIndex(2).next())
        assertEquals(TileSideIndex(4), TileSideIndex(3).next())
        assertEquals(TileSideIndex(5), TileSideIndex(4).next())
    }

    @Test
    fun `it gets the right previous`() {
        assertEquals(TileSideIndex(0), TileSideIndex(1).previous())
        assertEquals(TileSideIndex(1), TileSideIndex(2).previous())
        assertEquals(TileSideIndex(2), TileSideIndex(3).previous())
        assertEquals(TileSideIndex(3), TileSideIndex(4).previous())
        assertEquals(TileSideIndex(4), TileSideIndex(5).previous())
        assertEquals(TileSideIndex(5), TileSideIndex(0).previous())
    }

    @Test
    fun `it gets the right opposite`() {
        assertEquals(TileSideIndex(0), TileSideIndex(3).opposite())
        assertEquals(TileSideIndex(1), TileSideIndex(4).opposite())
        assertEquals(TileSideIndex(2), TileSideIndex(5).opposite())
        assertEquals(TileSideIndex(3), TileSideIndex(0).opposite())
        assertEquals(TileSideIndex(4), TileSideIndex(1).opposite())
        assertEquals(TileSideIndex(5), TileSideIndex(2).opposite())
    }

    @Test
    fun `it gets the right adjacent sides`() {
        assertEquals(
                listOf(TileCornerIndex(0), TileCornerIndex(1)),
                TileSideIndex(0).getAdjacentCorners())
        assertEquals(
                listOf(TileCornerIndex(1), TileCornerIndex(2)),
                TileSideIndex(1).getAdjacentCorners())
        assertEquals(
                listOf(TileCornerIndex(2), TileCornerIndex(3)),
                TileSideIndex(2).getAdjacentCorners())
        assertEquals(
                listOf(TileCornerIndex(3), TileCornerIndex(4)),
                TileSideIndex(3).getAdjacentCorners())
        assertEquals(
                listOf(TileCornerIndex(4), TileCornerIndex(5)),
                TileSideIndex(4).getAdjacentCorners())
        assertEquals(
                listOf(TileCornerIndex(5), TileCornerIndex(0)),
                TileSideIndex(5).getAdjacentCorners())
    }
}