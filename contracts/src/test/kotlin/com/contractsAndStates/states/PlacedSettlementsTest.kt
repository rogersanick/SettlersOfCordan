package com.contractsAndStates.states

import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlacedSettlementsTest {

    @Test
    fun `Constructor accepts proper input`() {
        PlacedSettlements(List(GameBoardState.TILE_COUNT) { List(HexTile.SIDE_COUNT) { false } })
    }

    @Test
    fun `Constructor rejects too short list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedSettlements(List(GameBoardState.TILE_COUNT - 1) { List(HexTile.SIDE_COUNT) { false } })
        }
    }

    @Test
    fun `Constructor rejects too short inner list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedSettlements(List(GameBoardState.TILE_COUNT) { List(HexTile.SIDE_COUNT - 1) { false } })
        }
    }

    @Test
    fun `Constructor rejects too long list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedSettlements(List(GameBoardState.TILE_COUNT + 1) { List(HexTile.SIDE_COUNT) { false } })
        }
    }

    @Test
    fun `Constructor rejects too long inner list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedSettlements(List(GameBoardState.TILE_COUNT) { List(HexTile.SIDE_COUNT + 1) { false } })
        }
    }

    @Test
    fun `hasOn after placeOn without sides is correct`() {
        val placed = PlacedSettlements.Builder(List(GameBoardState.TILE_COUNT) {
            MutableList(HexTile.SIDE_COUNT) { false }
        })
                .placeOn(HexTileIndex(3), TileCornerIndex(2))
                .placeOn(HexTileIndex(3), TileCornerIndex(1))
                .placeOn(HexTileIndex(7), TileCornerIndex(4))
                .build()
        assertTrue(placed.hasOn(HexTileIndex(3), TileCornerIndex(2)))
        assertTrue(placed.hasOn(HexTileIndex(3), TileCornerIndex(1)))
        assertTrue(placed.hasOn(HexTileIndex(7), TileCornerIndex(4)))
        assertFalse(placed.hasOn(HexTileIndex(7), TileCornerIndex(5)))
    }

    @Test
    fun `hasOn after placeOn with sides is correct`() {
        val builder = PlacedSettlements.Builder(List(GameBoardState.TILE_COUNT) {
            MutableList(HexTile.SIDE_COUNT) { false }
        })
        val sides = TileSides.Builder()
                .setNeighborOn(TileSideIndex(1), HexTileIndex(2))
                .build()
        val placed = builder
                .placeOn(HexTileIndex(3), TileCornerIndex(2), sides)
                .placeOn(HexTileIndex(3), TileCornerIndex(1), sides)
                .placeOn(HexTileIndex(7), TileCornerIndex(4))
                .build()
        assertTrue(placed.hasOn(HexTileIndex(3), TileCornerIndex(2)))
        assertTrue(placed.hasOn(HexTileIndex(2), TileCornerIndex(4)))
        assertTrue(placed.hasOn(HexTileIndex(3), TileCornerIndex(1)))
        assertTrue(placed.hasOn(HexTileIndex(7), TileCornerIndex(4)))
        assertFalse(placed.hasOn(HexTileIndex(7), TileCornerIndex(5)))
    }

    @Test
    fun `Builder-constructor accepts proper input`() {
        PlacedSettlements.Builder(List(GameBoardState.TILE_COUNT) { MutableList(HexTile.SIDE_COUNT) { false } })
    }

    @Test
    fun `Builder-constructor rejects too short list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedSettlements.Builder(List(GameBoardState.TILE_COUNT - 1) { MutableList(HexTile.SIDE_COUNT) { false } })
        }
    }

    @Test
    fun `Builder-constructor rejects too short inner list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedSettlements.Builder(List(GameBoardState.TILE_COUNT) { MutableList(HexTile.SIDE_COUNT - 1) { false } })
        }
    }

    @Test
    fun `Builder-constructor rejects too long list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedSettlements.Builder(List(GameBoardState.TILE_COUNT + 1) { MutableList(HexTile.SIDE_COUNT) { false } })
        }
    }

    @Test
    fun `Builder-constructor rejects too long inner list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedSettlements.Builder(List(GameBoardState.TILE_COUNT) { MutableList(HexTile.SIDE_COUNT + 1) { false } })
        }
    }

    @Test
    fun `Builder-placeOn without sides is correct`() {
        val builder = PlacedSettlements.Builder(List(GameBoardState.TILE_COUNT) {
            MutableList(HexTile.SIDE_COUNT) { false }
        })
        assertFalse(builder.hasOn(HexTileIndex(3), TileCornerIndex(2)))
        assertFalse(builder.hasOn(HexTileIndex(3), TileCornerIndex(1)))
        assertFalse(builder.hasOn(HexTileIndex(7), TileCornerIndex(4)))
        assertFalse(builder.hasOn(HexTileIndex(7), TileCornerIndex(5)))
        builder
                .placeOn(HexTileIndex(3), TileCornerIndex(2))
                .placeOn(HexTileIndex(3), TileCornerIndex(1))
                .placeOn(HexTileIndex(7), TileCornerIndex(4))
        assertTrue(builder.hasOn(HexTileIndex(3), TileCornerIndex(2)))
        assertTrue(builder.hasOn(HexTileIndex(3), TileCornerIndex(1)))
        assertTrue(builder.hasOn(HexTileIndex(7), TileCornerIndex(4)))
        assertFalse(builder.hasOn(HexTileIndex(7), TileCornerIndex(5)))
    }

    @Test
    fun `Builder-placeOn with sides is correct`() {
        val builder = PlacedSettlements.Builder(List(GameBoardState.TILE_COUNT) {
            MutableList(HexTile.SIDE_COUNT) { false }
        })
        val sides = TileSides.Builder()
                .setNeighborOn(TileSideIndex(1), HexTileIndex(2))
                .build()
        assertFalse(builder.hasOn(HexTileIndex(3), TileCornerIndex(2)))
        assertFalse(builder.hasOn(HexTileIndex(2), TileCornerIndex(4)))
        assertFalse(builder.hasOn(HexTileIndex(3), TileCornerIndex(1)))
        assertFalse(builder.hasOn(HexTileIndex(7), TileCornerIndex(4)))
        assertFalse(builder.hasOn(HexTileIndex(7), TileCornerIndex(5)))
        builder
                .placeOn(HexTileIndex(3), TileCornerIndex(2), sides)
                .placeOn(HexTileIndex(3), TileCornerIndex(1), sides)
                .placeOn(HexTileIndex(7), TileCornerIndex(4))
        assertTrue(builder.hasOn(HexTileIndex(3), TileCornerIndex(2)))
        assertTrue(builder.hasOn(HexTileIndex(2), TileCornerIndex(4)))
        assertTrue(builder.hasOn(HexTileIndex(3), TileCornerIndex(1)))
        assertTrue(builder.hasOn(HexTileIndex(7), TileCornerIndex(4)))
        assertFalse(builder.hasOn(HexTileIndex(7), TileCornerIndex(5)))
    }

}