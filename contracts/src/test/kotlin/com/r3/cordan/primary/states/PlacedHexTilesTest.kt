package com.r3.cordan.primary.states

import com.r3.cordan.oracle.client.states.RollTrigger
import com.r3.cordan.primary.states.board.*
import com.r3.cordan.primary.states.resources.HexTileType
import com.r3.cordan.primary.states.structure.GameBoardState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.internal.toMultiMap
import org.junit.jupiter.api.Test
import kotlin.test.*

class PlacedHexTilesTest {

    private fun getAllTileBuilders(): List<HexTile.Builder> {
        var tileIndex = 0
        return PlacedHexTiles.tileCountPerType.flatMap { entry ->
            (0 until entry.value).map {
                HexTile.Builder(HexTileIndex(tileIndex).also { tileIndex++ })
                        .with(entry.key)
                        .also { builder ->
                            if (entry.key != HexTileType.Desert) builder.with(RollTrigger(3))
                        }
                        .with(entry.key == HexTileType.Desert)
            }
        }
    }

    private fun buildWithBuilder() = PlacedHexTiles.Builder(getAllTileBuilders().toMutableList()).build()
    private fun getAllTiles() = getAllTileBuilders().map { it.build() }
    private fun Pair<Int, Int>.toAbsoluteSide() = AbsoluteSide(HexTileIndex(first), TileSideIndex(second))
    private fun List<Pair<Int, Int>>.toAbsoluteSides() = map { it.toAbsoluteSide() }
    private fun Pair<Int, Int>.toAbsoluteCorner() = AbsoluteCorner(HexTileIndex(first), TileCornerIndex(second))
    private fun List<Pair<Int, Int>>.toAbsoluteCorners() = map { it.toAbsoluteCorner() }

    @Test
    fun `Builder rejects too short list`() {
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles.Builder(getAllTileBuilders().dropLast(1).toMutableList())
        }
    }

    @Test
    fun `Builder default value is correct`() {
        val builder = PlacedHexTiles.Builder(PlacedHexTiles.Builder.createFullTileList())
        (0 until GameBoardState.TILE_COUNT).forEach {
            assertEquals(HexTileIndex(it), builder.get(HexTileIndex(it)).hexTileIndex)
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
    fun `Builder-assignShuffledTypes is correct`() {
        PlacedHexTiles.Builder.createFull()
                .build()
                .value
                .map { it.resourceType to 1 }
                .toMultiMap()
                .mapValues { it.value.sum() }
                .forEach { (type, count) ->
                    assertEquals(PlacedHexTiles.tileCountPerType[type], count)
                }
    }

    @Test
    fun `Builder-assignShuffledRollTriggers is correct`() {
        val placed = PlacedHexTiles.Builder.createFull()
                .build()
                .value
        placed.forEach {
            assertEquals(it.resourceType == HexTileType.Desert, it.rollTrigger == null)
        }
        assertEquals(PlacedHexTiles.rollTriggers, placed.mapNotNull { it.rollTrigger }.sortedBy { it.total })
    }

    @Test
    fun `Builder-buildRoadOn is correct`() {
        val placed = PlacedHexTiles.Builder(getAllTileBuilders().toMutableList())
                .setRoadOn((4 to 4).toAbsoluteSide(), UniqueIdentifier())
                .setRoadOn((2 to 0).toAbsoluteSide(), UniqueIdentifier())
                .build()

        assertFalse(placed.hasRoadOn((0 to 0).toAbsoluteSide()))
        assertTrue(placed.hasRoadOn((2 to 0).toAbsoluteSide()))
        assertTrue(placed.hasRoadOn((4 to 4).toAbsoluteSide()))
        assertTrue(placed.hasRoadOn((3 to 1).toAbsoluteSide()))
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
        val list = getAllTileBuilders().toMutableList()
        list[5] = list[4]
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles(list.map { it.build() })
        }
    }

    @Test
    fun `Constructor rejects unmatched index`() {
        val list = getAllTileBuilders().toMutableList()
        list[5] = list[5].let { element5 ->
            val element4 = list[4]
            list[4] = element5
            element4
        }
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles(list.map { it.build() })
        }
    }

    @Test
    fun `Constructor rejects double robber`() {
        val list = getAllTileBuilders().toMutableList()
        list[5] = HexTile.Builder(HexTileIndex(5))
                .with(HexTileType.Forest)
                .with(RollTrigger(3))
                .with(true)
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles(list.map { it.build() })
        }
    }

    @Test
    fun `Constructor rejects when missing tile type`() {
        val list = getAllTileBuilders().toMutableList()
        list[0] = HexTile.Builder(HexTileIndex(0))
                .with(HexTileType.Field)
                .with(RollTrigger(3))
                .with(true)
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles(list.map { it.build() })
        }
    }

    @Test
    fun `Constructor rejects wrong tile type count`() {
        val list = getAllTileBuilders().toMutableList()
        list[1] = HexTile.Builder(HexTileIndex(1))
                .with(HexTileType.Forest)
                .with(RollTrigger(3))
                .with(false)
        assertFailsWith<IllegalArgumentException> {
            PlacedHexTiles(list.map { it.build() })
        }
    }

    @Test
    fun `Constructor rejects null rollTrigger on non-desert tile`() {
        val builder = PlacedHexTiles.Builder(PlacedHexTiles.Builder.createFullTileList())
                .assignShuffledTypes()
        (0 until GameBoardState.TILE_COUNT).fold(false) { doneYet, index ->
            val tile = builder.get(HexTileIndex(index))
            if (tile.resourceType != HexTileType.Desert) {
                if (doneYet) tile.with(RollTrigger(2))
                true
            } else doneYet
        }
        assertFailsWith<java.lang.IllegalArgumentException> {
            builder.build()
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
        val unknownTile = HexTile.Builder(HexTileIndex(1))
                .with(HexTileType.Forest)
                .with(RollTrigger(3))
                .with(false)
                .build()

        assertEquals(HexTileIndex(4), placed.indexOf(placed.get(HexTileIndex(4))))
        assertEquals(HexTileIndex(14), placed.indexOf(placed.get(HexTileIndex(14))))
        assertEquals(null, placed.indexOf(unknownTile))
    }

    @Test
    fun `getOpposite is correct`() {
        val placed = buildWithBuilder()

        assertNull(placed.getOpposite((0 to 0).toAbsoluteSide()))
        assertEquals((1 to 4).toAbsoluteSide(), placed.getOpposite((0 to 1).toAbsoluteSide()))
        assertEquals((3 to 0).toAbsoluteSide(), placed.getOpposite((0 to 3).toAbsoluteSide()))
        assertNull(placed.getOpposite((0 to 4).toAbsoluteSide()))
        assertEquals((3 to 2).toAbsoluteSide(), placed.getOpposite((8 to 5).toAbsoluteSide()))
    }

    @Test
    fun `getSmallerEquivalent side is correct`() {
        val placed = buildWithBuilder()

        assertEquals((0 to 0).toAbsoluteSide(), placed.getSmallerEquivalent((0 to 0).toAbsoluteSide()))
        assertEquals((0 to 1).toAbsoluteSide(), placed.getSmallerEquivalent((0 to 1).toAbsoluteSide()))
        assertEquals((0 to 1).toAbsoluteSide(), placed.getSmallerEquivalent((1 to 4).toAbsoluteSide()))
        assertEquals((4 to 2).toAbsoluteSide(), placed.getSmallerEquivalent((4 to 2).toAbsoluteSide()))
        assertEquals((4 to 2).toAbsoluteSide(), placed.getSmallerEquivalent((9 to 5).toAbsoluteSide()))
    }

    @Test
    fun `getSideHash is correct`() {
        val placed = buildWithBuilder()

        assertEquals(0, placed.getSideHash((0 to 0).toAbsoluteSide()))
        assertEquals(1, placed.getSideHash((0 to 1).toAbsoluteSide()))
        assertEquals(1, placed.getSideHash((1 to 4).toAbsoluteSide()))
        assertEquals(5, placed.getSideHash((0 to 5).toAbsoluteSide()))
        assertEquals(6, placed.getSideHash((1 to 0).toAbsoluteSide()))
        assertEquals(86, placed.getSideHash((14 to 2).toAbsoluteSide()))
        assertEquals(86, placed.getSideHash((18 to 5).toAbsoluteSide()))
    }

    @Test
    fun `SideComparator is correct`() {
        val placed = buildWithBuilder()
        val sides = listOf(
                0 to 0, 18 to 2, 11 to 1, // orphans
                0 to 1, 1 to 4, // opposites
                9 to 5, 4 to 2 // opposites
        ).toAbsoluteSides()
        val sorted = sides
                .sortedWith(placed.getSideComparator())

        assertEquals(
                listOf(0 to 0, 0 to 1, 1 to 4, 4 to 2, 9 to 5, 11 to 1, 18 to 2).toAbsoluteSides(),
                sorted)
        assertEquals(
                listOf(0 to 0, 0 to 1, 4 to 2, 11 to 1, 18 to 2).toAbsoluteSides(),
                sorted.distinctBy { placed.getSideHash(it) })
    }

    @Test
    fun `getOverlappedCorners is correct`() {
        val placed = buildWithBuilder()

        assertEquals(
                listOf(null, null),
                placed.getOverlappedCorners((0 to 0).toAbsoluteCorner()))
        assertEquals(
                listOf(null, (1 to 5).toAbsoluteCorner()),
                placed.getOverlappedCorners((0 to 1).toAbsoluteCorner()))
        assertEquals(
                listOf((4 to 5).toAbsoluteCorner(), (3 to 1).toAbsoluteCorner()),
                placed.getOverlappedCorners((0 to 3).toAbsoluteCorner()))
        assertEquals(
                listOf((3 to 2).toAbsoluteCorner(), (4 to 4).toAbsoluteCorner()),
                placed.getOverlappedCorners((8 to 0).toAbsoluteCorner()))
        assertEquals(
                listOf((15 to 3).toAbsoluteCorner(), null),
                placed.getOverlappedCorners((18 to 1).toAbsoluteCorner()))
    }

    @Test
    fun `getSmallestEquivalent corner is correct`() {
        val placed = buildWithBuilder()

        assertEquals((0 to 0).toAbsoluteCorner(), placed.getSmallestEquivalent((0 to 0).toAbsoluteCorner()))
        assertEquals((0 to 1).toAbsoluteCorner(), placed.getSmallestEquivalent((0 to 1).toAbsoluteCorner()))
        assertEquals((0 to 1).toAbsoluteCorner(), placed.getSmallestEquivalent((1 to 5).toAbsoluteCorner()))
        assertEquals((0 to 4).toAbsoluteCorner(), placed.getSmallestEquivalent((0 to 4).toAbsoluteCorner()))
        assertEquals((0 to 4).toAbsoluteCorner(), placed.getSmallestEquivalent((3 to 0).toAbsoluteCorner()))
    }

    @Test
    fun `getCornerHash is correct`() {
        val placed = buildWithBuilder()

        assertEquals(0, placed.getCornerHash((0 to 0).toAbsoluteCorner()))
        assertEquals(1, placed.getCornerHash((0 to 1).toAbsoluteCorner()))
        assertEquals(1, placed.getCornerHash((1 to 5).toAbsoluteCorner()))
        assertEquals(3, placed.getCornerHash((0 to 3).toAbsoluteCorner()))
        assertEquals(3, placed.getCornerHash((3 to 1).toAbsoluteCorner()))
        assertEquals(3, placed.getCornerHash((4 to 5).toAbsoluteCorner()))
        assertEquals(86, placed.getCornerHash((14 to 2).toAbsoluteCorner()))
        assertEquals(86, placed.getCornerHash((18 to 0).toAbsoluteCorner()))
    }

    @Test
    fun `CornerComparator is correct`() {
        val placed = buildWithBuilder()
        val corners = listOf(
                0 to 0, 18 to 2, 11 to 1, // orphans
                0 to 1, 1 to 5, // overlapping
                9 to 5, 8 to 1, 4 to 3 // overlapping
        ).toAbsoluteCorners()
        val sorted = corners
                .sortedWith(placed.getCornerComparator())

        assertEquals(
                listOf(0 to 0, 0 to 1, 1 to 5, 4 to 3, 8 to 1, 9 to 5, 11 to 1, 18 to 2).toAbsoluteCorners(),
                sorted)
        assertEquals(
                listOf(0 to 0, 0 to 1, 4 to 3, 11 to 1, 18 to 2).toAbsoluteCorners(),
                sorted.distinctBy { placed.getCornerHash(it) })
    }
}
