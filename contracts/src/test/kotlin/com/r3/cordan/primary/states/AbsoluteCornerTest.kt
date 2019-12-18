package com.r3.cordan.primary.states

import com.r3.cordan.primary.states.board.*
import org.junit.jupiter.api.Test

import kotlin.test.assertEquals

class AbsoluteCornerTest {

    private fun Pair<Int, Int>.toAbsoluteSide() = AbsoluteSide(HexTileIndex(first), TileSideIndex(second))
    private fun Pair<Int, Int>.toAbsoluteCorner() = AbsoluteCorner(HexTileIndex(first), TileCornerIndex(second))

    @Test
    fun `previous is correct`() {
        assertEquals(
                (0 to 2).toAbsoluteCorner(),
                (0 to 3).toAbsoluteCorner().previous())
        assertEquals(
                (0 to 5).toAbsoluteCorner(),
                (0 to 0).toAbsoluteCorner().previous())
        assertEquals(
                (3 to 5).toAbsoluteCorner(),
                (3 to 0).toAbsoluteCorner().previous())
    }

    @Test
    fun `next is correct`() {
        assertEquals(
                (0 to 4).toAbsoluteCorner(),
                (0 to 3).toAbsoluteCorner().next())
        assertEquals(
                (0 to 1).toAbsoluteCorner(),
                (0 to 0).toAbsoluteCorner().next())
        assertEquals(
                (3 to 0).toAbsoluteCorner(),
                (3 to 5).toAbsoluteCorner().next())
    }

    @Test
    fun `getAdjacentSide is correct`() {
        assertEquals(
                listOf((0 to 2).toAbsoluteSide(), (0 to 3).toAbsoluteSide()),
                (0 to 3).toAbsoluteCorner().getAdjacentSides())
        assertEquals(
                listOf((5 to 4).toAbsoluteSide(), (5 to 5).toAbsoluteSide()),
                (5 to 5).toAbsoluteCorner().getAdjacentSides())
    }
}