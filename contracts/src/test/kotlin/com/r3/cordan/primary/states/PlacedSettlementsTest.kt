package com.r3.cordan.primary.states

import com.r3.cordan.primary.states.board.HexTile
import com.r3.cordan.primary.states.board.PlacedSettlements
import com.r3.cordan.primary.states.board.TileCornerIndex
import net.corda.core.contracts.UniqueIdentifier
import org.junit.jupiter.api.Test
import kotlin.test.*

class PlacedSettlementsTest {

    @Test
    fun `Constructor accepts proper input`() {
        PlacedSettlements(MutableList(HexTile.SIDE_COUNT) { null })
    }

    @Test
    fun `Constructor rejects too short list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedSettlements(MutableList(HexTile.SIDE_COUNT - 1) { null })
        }
    }

    @Test
    fun `Constructor rejects too long list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedSettlements(MutableList(HexTile.SIDE_COUNT + 1) { null })
        }
    }

    @Test
    fun `getSettlementOn and hasSettlementOn after placeOn is correct`() {
        val settlementIds = (1..3).map { UniqueIdentifier() }
        val placed = PlacedSettlements.Builder()
                .setSettlementOn(TileCornerIndex(2), settlementIds[0])
                .setSettlementOn(TileCornerIndex(1), settlementIds[1])
                .setSettlementOn(TileCornerIndex(4), settlementIds[2])
                .build()
        assertEquals(settlementIds[0], placed.getSettlementOn(TileCornerIndex(2)))
        assertTrue(placed.hasSettlementOn(TileCornerIndex(2)))
        assertEquals(settlementIds[1], placed.getSettlementOn(TileCornerIndex(1)))
        assertTrue(placed.hasSettlementOn(TileCornerIndex(1)))
        assertEquals(settlementIds[2], placed.getSettlementOn(TileCornerIndex(4)))
        assertTrue(placed.hasSettlementOn(TileCornerIndex(4)))
        assertNull(placed.getSettlementOn(TileCornerIndex(5)))
        assertFalse(placed.hasSettlementOn(TileCornerIndex(5)))
    }

    @Test
    fun `Builder-constructor accepts proper input`() {
        PlacedSettlements.Builder(MutableList(HexTile.SIDE_COUNT) { null })
    }

    @Test
    fun `Builder-constructor rejects too short list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedSettlements.Builder(MutableList(HexTile.SIDE_COUNT - 1) { null })
        }
    }

    @Test
    fun `Builder-constructor rejects too long list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedSettlements.Builder(MutableList(HexTile.SIDE_COUNT + 1) { null })
        }
    }

    @Test
    fun `Builder-constructor rejects non-null duplicates`() {
        val duplicate = UniqueIdentifier()
        val settlementIds = listOf(duplicate, duplicate, UniqueIdentifier())
        val placed = PlacedSettlements.Builder()
                .setSettlementOn(TileCornerIndex(2), settlementIds[0])
                .setSettlementOn(TileCornerIndex(1), settlementIds[1])
                .setSettlementOn(TileCornerIndex(4), settlementIds[2])

        assertFailsWith<IllegalArgumentException> {
            placed.build()
        }
    }

    @Test
    fun `Builder-setSettlementOn, getSettlementOn and hasSettlementOn after is correct`() {
        val settlementIds = (1..3).map { UniqueIdentifier() }
        val builder = PlacedSettlements.Builder()
        assertFalse(builder.hasSettlementOn(TileCornerIndex(2)))
        assertFalse(builder.hasSettlementOn(TileCornerIndex(1)))
        assertFalse(builder.hasSettlementOn(TileCornerIndex(4)))
        assertFalse(builder.hasSettlementOn(TileCornerIndex(5)))
        builder
                .setSettlementOn(TileCornerIndex(2), settlementIds[0])
                .setSettlementOn(TileCornerIndex(1), settlementIds[1])
                .setSettlementOn(TileCornerIndex(4), settlementIds[2])
        assertEquals(settlementIds[0], builder.getSettlementOn(TileCornerIndex(2)))
        assertTrue(builder.hasSettlementOn(TileCornerIndex(2)))
        assertEquals(settlementIds[1], builder.getSettlementOn(TileCornerIndex(1)))
        assertTrue(builder.hasSettlementOn(TileCornerIndex(1)))
        assertEquals(settlementIds[2], builder.getSettlementOn(TileCornerIndex(4)))
        assertTrue(builder.hasSettlementOn(TileCornerIndex(4)))
        assertNull(builder.getSettlementOn(TileCornerIndex(5)))
        assertFalse(builder.hasSettlementOn(TileCornerIndex(5)))
    }
}