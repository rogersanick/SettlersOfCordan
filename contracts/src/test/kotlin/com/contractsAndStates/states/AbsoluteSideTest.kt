package com.contractsAndStates.states

import org.junit.Test
import kotlin.test.assertEquals

class AbsoluteSideTest {

    private fun Pair<Int, Int>.toAbsoluteSide() = AbsoluteSide(HexTileIndex(first), TileSideIndex(second))
    private fun Pair<Int, Int>.toAbsoluteCorner() = AbsoluteCorner(HexTileIndex(first), TileCornerIndex(second))

    @Test
    fun `previous is correct`() {
        assertEquals(
                (0 to 2).toAbsoluteSide(),
                (0 to 3).toAbsoluteSide().previous())
        assertEquals(
                (0 to 5).toAbsoluteSide(),
                (0 to 0).toAbsoluteSide().previous())
        assertEquals(
                (3 to 5).toAbsoluteSide(),
                (3 to 0).toAbsoluteSide().previous())
    }

    @Test
    fun `next is correct`() {
        assertEquals(
                (0 to 4).toAbsoluteSide(),
                (0 to 3).toAbsoluteSide().next())
        assertEquals(
                (0 to 1).toAbsoluteSide(),
                (0 to 0).toAbsoluteSide().next())
        assertEquals(
                (3 to 0).toAbsoluteSide(),
                (3 to 5).toAbsoluteSide().next())
    }

    @Test
    fun `getAdjacentCorners is correct`() {
        assertEquals(
                listOf((0 to 3).toAbsoluteCorner(), (0 to 4).toAbsoluteCorner()),
                (0 to 3).toAbsoluteSide().getAdjacentCorners())
        assertEquals(
                listOf((5 to 5).toAbsoluteCorner(), (5 to 0).toAbsoluteCorner()),
                (5 to 5).toAbsoluteSide().getAdjacentCorners())
    }
}