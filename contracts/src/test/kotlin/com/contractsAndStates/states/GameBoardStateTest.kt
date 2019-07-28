package com.contractsAndStates.states

import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameBoardStateTest {

    private fun boardBuilder(linearId: UniqueIdentifier = UniqueIdentifier()) =
            GameBoardState.Builder(
                    linearId = linearId,
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

    @Test
    fun `ownPointer is correct`() {
        val linearId = UniqueIdentifier()
        val boardBuilder = boardBuilder(linearId)
        assertEquals(boardBuilder.ownPointer().pointer, linearId)
        assertEquals(boardBuilder.ownPointer().type, GameBoardState::class.java)

        val state = boardBuilder.build()
        assertEquals(state.ownPointer().pointer, linearId)
        assertEquals(state.ownPointer().type, GameBoardState::class.java)
    }

    @Test
    fun `isValid TurnTracker is correct`() {
        val state = boardBuilder().build()
        val turnTracker = TurnTrackerState(
                gameBoardPointer = state.ownPointer(),
                participants = listOf(),
                linearId = state.turnTrackerLinearId)

        assertTrue(state.isValid(turnTracker))
    }

    @Test
    fun `isValid rejects if pointer is wrong`() {
        val state = boardBuilder().build()
        val turnTracker = TurnTrackerState(
                gameBoardPointer = LinearPointer(UniqueIdentifier(), GameBoardState::class.java),
                participants = listOf(),
                linearId = state.turnTrackerLinearId)

        assertFalse(state.isValid(turnTracker))
    }

    @Test
    fun `isValid rejects if turn tracker id is wrong`() {
        val state = boardBuilder().build()
        val turnTracker = TurnTrackerState(
                gameBoardPointer = state.ownPointer(),
                participants = listOf())

        assertFalse(state.isValid(turnTracker))
    }
}
