package com.contractsAndStates.states

import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameBoardStateTest {

    private fun boardBuilder() = GameBoardState.Builder(
            hexTiles = PlacedHexTiles.Builder.createFull(),
            ports = PlacedPorts.Builder.createAllPorts(),
            turnTrackerLinearId = UniqueIdentifier(),
            robberLinearId = UniqueIdentifier())

    private fun Pair<Int, Int>.toAbsoluteCorner() = AbsoluteCorner(HexTileIndex(first), TileCornerIndex(second))

    @Test
    fun `getSettlementsCount is correct`() {
        val boardBuilder = boardBuilder()
        boardBuilder.setSettlementOn((1 to 2).toAbsoluteCorner(), UniqueIdentifier())
                .setSettlementOn((2 to 1).toAbsoluteCorner(), UniqueIdentifier())
                .setSettlementOn((5 to 4).toAbsoluteCorner(), UniqueIdentifier())
        val boardState = boardBuilder.build()

        assertEquals(3, boardState.getSettlementsCount())
    }

    @Test
    fun `hasSettlementOn is correct`() {
        val boardBuilder = boardBuilder()
        boardBuilder
                .setSettlementOn((1 to 2).toAbsoluteCorner(), UniqueIdentifier())
                .setSettlementOn((1 to 1).toAbsoluteCorner(), UniqueIdentifier())
                .setSettlementOn((7 to 4).toAbsoluteCorner(), UniqueIdentifier())
        val boardState = boardBuilder.build()

        assertFalse(boardState.hasSettlementOn((1 to 0).toAbsoluteCorner()))
        assertTrue(boardState.hasSettlementOn((1 to 2).toAbsoluteCorner()))
        assertTrue(boardState.hasSettlementOn((2 to 4).toAbsoluteCorner()))
        assertTrue(boardState.hasSettlementOn((5 to 0).toAbsoluteCorner()))
        assertTrue(boardState.hasSettlementOn((1 to 1).toAbsoluteCorner()))
        assertTrue(boardState.hasSettlementOn((2 to 5).toAbsoluteCorner()))
        assertTrue(boardState.hasSettlementOn((7 to 4).toAbsoluteCorner()))
        assertFalse(boardState.hasSettlementOn((7 to 5).toAbsoluteCorner()))
    }
}
