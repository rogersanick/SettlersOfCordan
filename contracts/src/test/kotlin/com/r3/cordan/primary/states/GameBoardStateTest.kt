package com.r3.cordan.primary.states

import com.nhaarman.mockito_kotlin.whenever
import com.r3.cordan.primary.states.board.*
import com.r3.cordan.primary.states.robber.RobberState
import com.r3.cordan.primary.states.structure.GameBoardState
import com.r3.cordan.primary.states.trade.TradeState
import com.r3.cordan.primary.states.turn.TurnTrackerState
import net.corda.core.contracts.UniqueIdentifier
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
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
    fun `isValid TurnTracker is correct`() {
        val state = boardBuilder().build()
        val turnTracker = mock(TurnTrackerState::class.java)
        whenever(turnTracker.linearId).then { state.turnTrackerLinearId }
        whenever(turnTracker.gameBoardLinearId).then { state.linearId }

        assertTrue(state.isValid(turnTracker))
    }

    @Test
    fun `isValid TurnTracker rejects if pointer is wrong`() {
        val state = boardBuilder().build()
        val turnTracker = mock(TurnTrackerState::class.java)
        whenever(turnTracker.linearId).then { state.turnTrackerLinearId }
        whenever(turnTracker.gameBoardLinearId).then { UniqueIdentifier() }

        assertFalse(state.isValid(turnTracker))
    }

    @Test
    fun `isValid TurnTracker rejects if turn tracker id is wrong`() {
        val state = boardBuilder().build()
        val turnTracker = mock(TurnTrackerState::class.java)
        whenever(turnTracker.linearId).then { UniqueIdentifier() }
        whenever(turnTracker.gameBoardLinearId).then { state.linearId }

        assertFalse(state.isValid(turnTracker))
    }

    @Test
    fun `isValid Robber is correct`() {
        val state = boardBuilder().build()
        val robber = mock(RobberState::class.java)
        whenever(robber.linearId).then { state.robberLinearId }
        whenever(robber.gameBoardLinearId).then { state.linearId }

        assertTrue(state.isValid(robber))
    }

    @Test
    fun `isValid Robber rejects if pointer is wrong`() {
        val state = boardBuilder().build()
        val robber = mock(RobberState::class.java)
        whenever(robber.linearId).then { state.robberLinearId }
        whenever(robber.gameBoardLinearId).then { UniqueIdentifier() }

        assertFalse(state.isValid(robber))
    }

    @Test
    fun `isValid Robber rejects if turn tracker id is wrong`() {
        val state = boardBuilder().build()
        val robber = mock(RobberState::class.java)
        whenever(robber.linearId).then { UniqueIdentifier() }
        whenever(robber.gameBoardLinearId).then { state.linearId }


        assertFalse(state.isValid(robber))
    }

    @Test
    fun `isValid Trade is correct`() {
        val state = boardBuilder().build()
        val trade = mock(TradeState::class.java)
        whenever(trade.gameBoardLinearId).then { state.linearId }

        assertTrue(state.isValid(trade))
    }

    @Test
    fun `isValid Trade rejects if game board linearId is wrong`() {
        val state = boardBuilder().build()
        val trade = mock(TradeState::class.java)
        whenever(trade.gameBoardLinearId).then { UniqueIdentifier() }

        assertFalse(state.isValid(trade))
    }
}
