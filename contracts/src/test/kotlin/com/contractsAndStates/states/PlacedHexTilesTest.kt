package com.contractsAndStates.states

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlacedHexTilesTest {

    private fun getAllTileBuilders(): List<HexTile.Builder> {
        var tileIndex = 0
        return PlacedHexTiles.TILE_COUNT_PER_RESOURCE.flatMap { entry ->
            (0 until entry.value).map {
                HexTile.Builder()
                        .with(entry.key)
                        .with(1)
                        .with(entry.key == HexTileType.Desert)
                        .with(HexTileIndex(tileIndex).also { tileIndex++ })
            }
        }
    }

    private fun buildWithBuilder() = PlacedHexTiles.Builder(getAllTileBuilders().toMutableList()).build()

    private fun getAllTiles() = getAllTileBuilders().map { it.build() }

    @Test
    fun `Builder rejects too short list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles.Builder(getAllTileBuilders().dropLast(1).toMutableList())
        }
    }

    @Test
    fun `Builder-build is correct with first row`() {
        val placed = buildWithBuilder()

        // First tile, clockwise neighbors
        assertEquals(
                HexTileIndex(1),
                placed.value[0].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(4),
                placed.value[0].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(3),
                placed.value[0].sides.getNeighborOn(TileSideIndex(3)))

        // Second tile
        assertEquals(
                HexTileIndex(2),
                placed.value[1].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(5),
                placed.value[1].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(4),
                placed.value[1].sides.getNeighborOn(TileSideIndex(3)))
        assertEquals(
                HexTileIndex(0),
                placed.value[1].sides.getNeighborOn(TileSideIndex(4)))

        // Third tile
        assertEquals(
                HexTileIndex(6),
                placed.value[2].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(5),
                placed.value[2].sides.getNeighborOn(TileSideIndex(3)))
        assertEquals(
                HexTileIndex(1),
                placed.value[2].sides.getNeighborOn(TileSideIndex(4)))
    }

    @Test
    fun `Builder-build is correct with second row`() {
        val placed = buildWithBuilder()

        // First tile, clockwise neighbors
        assertEquals(
                HexTileIndex(0),
                placed.value[3].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(4),
                placed.value[3].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(8),
                placed.value[3].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(7),
                placed.value[3].sides.getNeighborOn(TileSideIndex(3)))

        // Second tile
        assertEquals(
                HexTileIndex(1),
                placed.value[4].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(5),
                placed.value[4].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(9),
                placed.value[4].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(8),
                placed.value[4].sides.getNeighborOn(TileSideIndex(3)))
        assertEquals(
                HexTileIndex(3),
                placed.value[4].sides.getNeighborOn(TileSideIndex(4)))
        assertEquals(
                HexTileIndex(0),
                placed.value[4].sides.getNeighborOn(TileSideIndex(5)))

        // Third tile
        assertEquals(
                HexTileIndex(2),
                placed.value[5].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(6),
                placed.value[5].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(10),
                placed.value[5].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(9),
                placed.value[5].sides.getNeighborOn(TileSideIndex(3)))
        assertEquals(
                HexTileIndex(4),
                placed.value[5].sides.getNeighborOn(TileSideIndex(4)))
        assertEquals(
                HexTileIndex(1),
                placed.value[5].sides.getNeighborOn(TileSideIndex(5)))

        // Fourth tile
        assertEquals(
                HexTileIndex(11),
                placed.value[6].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(10),
                placed.value[6].sides.getNeighborOn(TileSideIndex(3)))
        assertEquals(
                HexTileIndex(5),
                placed.value[6].sides.getNeighborOn(TileSideIndex(4)))
        assertEquals(
                HexTileIndex(2),
                placed.value[6].sides.getNeighborOn(TileSideIndex(5)))
    }

    @Test
    fun `Builder-build is correct with third row`() {
        val placed = buildWithBuilder()

        // First tile, clockwise neighbors
        assertEquals(
                HexTileIndex(3),
                placed.value[7].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(8),
                placed.value[7].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(12),
                placed.value[7].sides.getNeighborOn(TileSideIndex(2)))

        // Second tile
        assertEquals(
                HexTileIndex(4),
                placed.value[8].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(9),
                placed.value[8].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(13),
                placed.value[8].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(12),
                placed.value[8].sides.getNeighborOn(TileSideIndex(3)))
        assertEquals(
                HexTileIndex(7),
                placed.value[8].sides.getNeighborOn(TileSideIndex(4)))
        assertEquals(
                HexTileIndex(3),
                placed.value[8].sides.getNeighborOn(TileSideIndex(5)))

        // Third tile
        assertEquals(
                HexTileIndex(5),
                placed.value[9].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(10),
                placed.value[9].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(14),
                placed.value[9].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(13),
                placed.value[9].sides.getNeighborOn(TileSideIndex(3)))
        assertEquals(
                HexTileIndex(8),
                placed.value[9].sides.getNeighborOn(TileSideIndex(4)))
        assertEquals(
                HexTileIndex(4),
                placed.value[9].sides.getNeighborOn(TileSideIndex(5)))

        // Fourth tile
        assertEquals(
                HexTileIndex(6),
                placed.value[10].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(11),
                placed.value[10].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(15),
                placed.value[10].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(14),
                placed.value[10].sides.getNeighborOn(TileSideIndex(3)))
        assertEquals(
                HexTileIndex(9),
                placed.value[10].sides.getNeighborOn(TileSideIndex(4)))
        assertEquals(
                HexTileIndex(5),
                placed.value[10].sides.getNeighborOn(TileSideIndex(5)))

        // Fifth tile
        assertEquals(
                HexTileIndex(15),
                placed.value[11].sides.getNeighborOn(TileSideIndex(3)))
        assertEquals(
                HexTileIndex(10),
                placed.value[11].sides.getNeighborOn(TileSideIndex(4)))
        assertEquals(
                HexTileIndex(6),
                placed.value[11].sides.getNeighborOn(TileSideIndex(5)))
    }

    @Test
    fun `Builder-build is correct with fourth row`() {
        val placed = buildWithBuilder()

        // First tile, clockwise neighbors
        assertEquals(
                HexTileIndex(8),
                placed.value[12].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(13),
                placed.value[12].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(16),
                placed.value[12].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(7),
                placed.value[12].sides.getNeighborOn(TileSideIndex(5)))

        // Second tile
        assertEquals(
                HexTileIndex(9),
                placed.value[13].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(14),
                placed.value[13].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(17),
                placed.value[13].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(16),
                placed.value[13].sides.getNeighborOn(TileSideIndex(3)))
        assertEquals(
                HexTileIndex(12),
                placed.value[13].sides.getNeighborOn(TileSideIndex(4)))
        assertEquals(
                HexTileIndex(8),
                placed.value[13].sides.getNeighborOn(TileSideIndex(5)))

        // Third tile
        assertEquals(
                HexTileIndex(10),
                placed.value[14].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(15),
                placed.value[14].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(18),
                placed.value[14].sides.getNeighborOn(TileSideIndex(2)))
        assertEquals(
                HexTileIndex(17),
                placed.value[14].sides.getNeighborOn(TileSideIndex(3)))
        assertEquals(
                HexTileIndex(13),
                placed.value[14].sides.getNeighborOn(TileSideIndex(4)))
        assertEquals(
                HexTileIndex(9),
                placed.value[14].sides.getNeighborOn(TileSideIndex(5)))

        // Fourth tile
        assertEquals(
                HexTileIndex(11),
                placed.value[15].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(18),
                placed.value[15].sides.getNeighborOn(TileSideIndex(3)))
        assertEquals(
                HexTileIndex(14),
                placed.value[15].sides.getNeighborOn(TileSideIndex(4)))
        assertEquals(
                HexTileIndex(10),
                placed.value[15].sides.getNeighborOn(TileSideIndex(5)))
    }

    @Test
    fun `Builder-build is correct with fifth row`() {
        val placed = buildWithBuilder()

        // First tile, clockwise neighbors
        assertEquals(
                HexTileIndex(13),
                placed.value[16].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(17),
                placed.value[16].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(12),
                placed.value[16].sides.getNeighborOn(TileSideIndex(5)))

        // Second tile
        assertEquals(
                HexTileIndex(14),
                placed.value[17].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(18),
                placed.value[17].sides.getNeighborOn(TileSideIndex(1)))
        assertEquals(
                HexTileIndex(16),
                placed.value[17].sides.getNeighborOn(TileSideIndex(4)))
        assertEquals(
                HexTileIndex(13),
                placed.value[17].sides.getNeighborOn(TileSideIndex(5)))

        // Third tile
        assertEquals(
                HexTileIndex(15),
                placed.value[18].sides.getNeighborOn(TileSideIndex(0)))
        assertEquals(
                HexTileIndex(17),
                placed.value[18].sides.getNeighborOn(TileSideIndex(4)))
        assertEquals(
                HexTileIndex(14),
                placed.value[18].sides.getNeighborOn(TileSideIndex(5)))
    }

    @Test
    fun `Constructor accepts straight build`() {
        PlacedHexTiles(getAllTiles()).value.forEachIndexed { index, it ->
            assertEquals(index, it.hexTileIndex.value)
        }
    }

    @Test
    fun `Constructor rejects too short list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles(getAllTiles().dropLast(1))
        }
    }

    @Test
    fun `Constructor rejects too long list`() {
        val list = getAllTiles()
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles(list.plus(list.takeLast(1)))
        }
    }

    @Test
    fun `Constructor rejects duplicate index`() {
        val list = getAllTileBuilders()
        list[5].with(HexTileIndex(4))
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles(list.map { it.build() })
        }
    }

    @Test
    fun `Constructor rejects unmatched index`() {
        val list = getAllTileBuilders()
        list[5].with(HexTileIndex(4))
        list[4].with(HexTileIndex(5))
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles(list.map { it.build() })
        }
    }

    @Test
    fun `Constructor rejects double robber`() {
        val list = getAllTileBuilders()
        list[5].with(true)
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles(list.map { it.build() })
        }
    }

    @Test
    fun `Constructor rejects when missing tile type`() {
        val list = getAllTileBuilders()
        list[0].with(HexTileType.Field)
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles(list.map { it.build() })
        }
    }

    @Test
    fun `Constructor rejects wrong tile type count`() {
        val list = getAllTileBuilders()
        list[1].with(HexTileType.Forest)
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles(list.map { it.build() })
        }
    }

    @Test
    fun `get returns proper element`() {
        val placed = buildWithBuilder()

        assertEquals(HexTileIndex(3), placed.get(HexTileIndex(3)).hexTileIndex)
        assertEquals(HexTileIndex(13), placed.get(HexTileIndex(13)).hexTileIndex)
    }

    @Test
    fun `indexOf returns proper element`() {
        val placed = buildWithBuilder()
        val unknownTile = HexTile.Builder()
                .with(HexTileType.Forest)
                .with(1)
                .with(false)
                .with(HexTileIndex(1))
                .build()

        assertEquals(HexTileIndex(4), placed.indexOf(placed.get(HexTileIndex(4))))
        assertEquals(HexTileIndex(14), placed.indexOf(placed.get(HexTileIndex(14))))
        assertEquals(null, placed.indexOf(unknownTile))
    }
}
