package com.contractsAndStates.states

import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TileSidesTest {

    private fun List<Int?>.makeNeighbors() = map {
        if (it == null) null
        else HexTileIndex(it)
    }

    private fun TileSides.Builder.addNeighbors(neighbors: List<Int?>) = apply {
        neighbors.forEachIndexed { index, it ->
            if (it != null) setNeighborOn(TileSideIndex(index), HexTileIndex(it))
        }
    }

    private fun TileSides.Builder.addRoads(roads: List<UniqueIdentifier?>) = apply {
        roads.forEachIndexed { index, it ->
            if (it != null) setRoadIdOn(TileSideIndex(index), it)
        }
    }

    @Test
    fun `it rejects a too short sides list`() {
        assertFailsWith<IllegalArgumentException> {
            TileSides(List(HexTile.SIDE_COUNT - 1) { TileSide() })
        }
    }

    @Test
    fun `it rejects a too large neighbors list`() {
        assertFailsWith<IllegalArgumentException> {
            TileSides(List(HexTile.SIDE_COUNT + 1) { TileSide() })
        }
    }

    @Test
    fun `it rejects when there is a duplicate index in neighbors`() {
        assertFailsWith<IllegalArgumentException> {
            TileSides.Builder().addNeighbors(listOf(2, null, null, 0, 2, 12)).build()
        }
    }

    @Test
    fun `it rejects when there is a duplicate id in roads`() {
        val id = UniqueIdentifier()
        assertFailsWith<IllegalArgumentException> {
            TileSides.Builder().addRoads(listOf(id, null, null, UniqueIdentifier(), id, UniqueIdentifier())).build()
        }
    }

    @Test
    fun `value is what you pass to the constructor`() {
        val ids = listOf(UniqueIdentifier(), null, null, UniqueIdentifier(), UniqueIdentifier(), null)
        val straight1 = listOf(2, null, null, 0, 5, 12).makeNeighbors()
                .mapIndexed { index, it ->
                    TileSide(it, ids[index])
                }
        val built1 = TileSides.Builder()
                .addNeighbors(listOf(2, null, null, 0, 5, 12))
                .addRoads(ids)
                .build().value
        straight1.forEachIndexed { index, it ->
            assertEquals(it, built1[index])
        }

        val straight2 = listOf(2, 4, 1, 0, 5, 12).makeNeighbors()
                .mapIndexed { index, it ->
                    TileSide(it, ids[index])
                }
        val built2 = TileSides.Builder()
                .addNeighbors(listOf(2, 4, 1, 0, 5, 12))
                .addRoads(ids)
                .build().value
        straight2.forEachIndexed { index, it ->
            assertEquals(it, built2[index])
        }
    }

    @Test
    fun `Builder-getNeighborOn is correct`() {
        val builder = TileSides.Builder().addNeighbors(listOf(2, null, null, 0, 5, 12))
        assertEquals(HexTileIndex(2), builder.getNeighborOn(TileSideIndex(0)))
        assertEquals(null, builder.getNeighborOn(TileSideIndex(1)))
        assertEquals(null, builder.getNeighborOn(TileSideIndex(2)))
        assertEquals(HexTileIndex(0), builder.getNeighborOn(TileSideIndex(3)))
        assertEquals(HexTileIndex(5), builder.getNeighborOn(TileSideIndex(4)))
        assertEquals(HexTileIndex(12), builder.getNeighborOn(TileSideIndex(5)))
    }

    @Test
    fun `Builder-getRoadIdOn is correct`() {
        val ids = listOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val builder = TileSides.Builder().addRoads(ids)
        assertEquals(ids[0], builder.getRoadIdOn(TileSideIndex(0)))
        assertEquals(null, builder.getRoadIdOn(TileSideIndex(1)))
        assertEquals(null, builder.getRoadIdOn(TileSideIndex(2)))
        assertEquals(ids[3], builder.getRoadIdOn(TileSideIndex(3)))
        assertEquals(ids[4], builder.getRoadIdOn(TileSideIndex(4)))
        assertEquals(ids[5], builder.getRoadIdOn(TileSideIndex(5)))
    }

    @Test
    fun `Builder-setRoadIdOn rejects if already set`() {
        val indices = listOf(2, null, null, 0, 5, 12)
        val ids = listOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val builder = TileSides.Builder()
                .addNeighbors(indices)
                .addRoads(ids)

        assertFailsWith<java.lang.IllegalArgumentException> {
            builder.setRoadIdOn(TileSideIndex(0), UniqueIdentifier())
        }
    }

    @Test
    fun `Builder-setRoadIdOn accepts idempotent`() {
        val indices = listOf(2, null, null, 0, 5, 12)
        val ids = mutableListOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val builder = TileSides.Builder()
                .addNeighbors(indices)
                .addRoads(ids)
                .setRoadIdOn(TileSideIndex(0), ids[0]!!)

        assertEquals(ids[0], builder.getRoadIdOn(TileSideIndex(0)))
    }

    @Test
    fun `Builder-setRoadIdOn accepts setting id`() {
        val indices = listOf(2, null, null, 0, 5, 12)
        val ids = mutableListOf(null, null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val builder = TileSides.Builder()
                .addNeighbors(indices)
                .addRoads(ids)
        ids[0] = UniqueIdentifier()
        builder.setRoadIdOn(TileSideIndex(0), ids[0]!!)

        assertEquals(ids[0], builder.getRoadIdOn(TileSideIndex(0)))
    }

    @Test
    fun `Builder-getSideOn is correct`() {
        val indices = listOf(2, null, null, 0, 5, 12)
        val tileIndices = indices.makeNeighbors()
        val ids = listOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val builder = TileSides.Builder()
                .addNeighbors(indices)
                .addRoads(ids)
        tileIndices.forEachIndexed { index, it ->
            assertEquals(
                    TileSide(it, ids[index]),
                    builder.getSideOn(TileSideIndex(index)))
        }
    }

    @Test
    fun `Builder-setSideOn rejects if already set`() {
        val indices = listOf(2, null, null, 0, 5, 12)
        val ids = listOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val builder = TileSides.Builder()
                .addNeighbors(indices)
                .addRoads(ids)

        assertFailsWith<java.lang.IllegalArgumentException> {
            builder.setSideOn(TileSideIndex(0), TileSide(HexTileIndex(3), null))
        }
        assertFailsWith<java.lang.IllegalArgumentException> {
            builder.setSideOn(TileSideIndex(0), TileSide(HexTileIndex(2), UniqueIdentifier()))
        }
    }

    @Test
    fun `Builder-setSideOn accepts if idempotent with road`() {
        val indices = listOf(2, null, null, 0, 5, 12)
        val tileIndices = indices.makeNeighbors()
        val ids = listOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val builder = TileSides.Builder()
                .addNeighbors(indices)
                .addRoads(ids)
                .setSideOn(TileSideIndex(0), TileSide(HexTileIndex(2), ids[0]))

        tileIndices.forEachIndexed { index, it ->
            assertEquals(
                    TileSide(it, ids[index]),
                    builder.getSideOn(TileSideIndex(index)))
        }
    }

    @Test
    fun `Builder-setSideOn accepts if idempotent with null road`() {
        val indices = listOf(2, null, null, 0, 5, 12)
        val tileIndices = indices.makeNeighbors()
        val ids = listOf(null, null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val builder = TileSides.Builder()
                .addNeighbors(indices)
                .addRoads(ids)
                .setSideOn(TileSideIndex(0), TileSide(HexTileIndex(2), null))

        tileIndices.forEachIndexed { index, it ->
            assertEquals(
                    TileSide(it, ids[index]),
                    builder.getSideOn(TileSideIndex(index)))
        }
    }

    @Test
    fun `Builder-setSideOn accepts a road setting`() {
        val indices = listOf(2, null, null, 0, 5, 12)
        val tileIndices = indices.makeNeighbors()
        val ids = mutableListOf(null, null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val builder = TileSides.Builder()
                .addNeighbors(indices)
                .addRoads(ids)
        ids[0] = UniqueIdentifier()
        builder.setSideOn(TileSideIndex(0), TileSide(HexTileIndex(2), ids[0]))

        tileIndices.forEachIndexed { index, it ->
            assertEquals(
                    TileSide(it, ids[index]),
                    builder.getSideOn(TileSideIndex(index)))
        }
    }

    @Test
    fun `Builder-setSideOn is correct`() {
        val indices = listOf(2, null, null, 0, 5, 12)
        val ids = listOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val builder = TileSides.Builder()
                .addNeighbors(indices)
                .addRoads(ids)
                .setSideOn(TileSideIndex(1), TileSide(HexTileIndex(3), null))

        listOf(2, 3, null, 0, 5, 12).makeNeighbors().forEachIndexed { index, it ->
            assertEquals(
                    TileSide(it, ids[index]),
                    builder.getSideOn(TileSideIndex(index)))
        }
    }

    @Test
    fun `Builder-getOverlappedCorners is correct`() {
        val neighbors = TileSides.Builder().addNeighbors(listOf(2, null, null, 0, 5, 12))
        assertEquals(
                listOf(
                        AbsoluteCorner(HexTileIndex(12), TileCornerIndex(2)),
                        AbsoluteCorner(HexTileIndex(2), TileCornerIndex(4))
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(0))
        )
        assertEquals(
                listOf(
                        AbsoluteCorner(HexTileIndex(2), TileCornerIndex(3)),
                        null
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(1))
        )
        assertEquals(
                listOf(
                        null,
                        null
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(2))
        )
        assertEquals(
                listOf(
                        null,
                        AbsoluteCorner(HexTileIndex(0), TileCornerIndex(1))
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(3))
        )
        assertEquals(
                listOf(
                        AbsoluteCorner(HexTileIndex(0), TileCornerIndex(0)),
                        AbsoluteCorner(HexTileIndex(5), TileCornerIndex(2))
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(4))
        )
        assertEquals(
                listOf(
                        AbsoluteCorner(HexTileIndex(5), TileCornerIndex(1)),
                        AbsoluteCorner(HexTileIndex(12), TileCornerIndex(3))
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(5))
        )
    }

    @Test
    fun `Builder-build is correct`() {
        val indices = listOf(2, null, null, 0, 5, 12)
        val tileIndices = indices.makeNeighbors()
        val ids = listOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val built = TileSides.Builder()
                .addNeighbors(indices)
                .addRoads(ids)
                .build()
        val directly = TileSides(ids.mapIndexed { index, it ->
            TileSide(tileIndices[index], it)
        })
        built.value.forEachIndexed { index, it ->
            assertEquals(it, directly.value[index])
        }
    }

    @Test
    fun `getSideOn is correct`() {
        val indices = listOf(2, null, null, 0, 5, 12)
        val tileIndices = indices.makeNeighbors()
        val ids = listOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val built = TileSides.Builder()
                .addNeighbors(indices)
                .addRoads(ids)
                .build()
        tileIndices.forEachIndexed { index, it ->
            assertEquals(
                    TileSide(it, ids[index]),
                    built.getSideOn(TileSideIndex(index)))
        }
    }

    @Test
    fun `getNeighborOn is correct`() {
        val neighbors = TileSides.Builder().addNeighbors(listOf(2, null, null, 0, 5, 12)).build()
        assertEquals(HexTileIndex(2), neighbors.getNeighborOn(TileSideIndex(0)))
        assertEquals(null, neighbors.getNeighborOn(TileSideIndex(1)))
        assertEquals(null, neighbors.getNeighborOn(TileSideIndex(2)))
        assertEquals(HexTileIndex(0), neighbors.getNeighborOn(TileSideIndex(3)))
        assertEquals(HexTileIndex(5), neighbors.getNeighborOn(TileSideIndex(4)))
        assertEquals(HexTileIndex(12), neighbors.getNeighborOn(TileSideIndex(5)))
    }

    @Test
    fun `hasNeighborOn is correct`() {
        val neighbors = TileSides.Builder().addNeighbors(listOf(2, null, null, 0, 5, 12)).build()
        assertTrue(neighbors.hasNeighborOn(TileSideIndex(0)))
        assertFalse(neighbors.hasNeighborOn(TileSideIndex(1)))
        assertFalse(neighbors.hasNeighborOn(TileSideIndex(2)))
        assertTrue(neighbors.hasNeighborOn(TileSideIndex(3)))
        assertTrue(neighbors.hasNeighborOn(TileSideIndex(4)))
        assertTrue(neighbors.hasNeighborOn(TileSideIndex(5)))
    }

    @Test
    fun `getRoadIdOn is correct`() {
        val ids = listOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val neighbors = TileSides.Builder().addRoads(ids).build()
        assertEquals(ids[0], neighbors.getRoadIdOn(TileSideIndex(0)))
        assertEquals(null, neighbors.getRoadIdOn(TileSideIndex(1)))
        assertEquals(null, neighbors.getRoadIdOn(TileSideIndex(2)))
        assertEquals(ids[3], neighbors.getRoadIdOn(TileSideIndex(3)))
        assertEquals(ids[4], neighbors.getRoadIdOn(TileSideIndex(4)))
        assertEquals(ids[5], neighbors.getRoadIdOn(TileSideIndex(5)))
    }

    @Test
    fun `hasRoadIdOn is correct`() {
        val ids = listOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val neighbors = TileSides.Builder().addRoads(ids).build()
        assertTrue(neighbors.hasRoadIdOn(TileSideIndex(0)))
        assertFalse(neighbors.hasRoadIdOn(TileSideIndex(1)))
        assertFalse(neighbors.hasRoadIdOn(TileSideIndex(2)))
        assertTrue(neighbors.hasRoadIdOn(TileSideIndex(3)))
        assertTrue(neighbors.hasRoadIdOn(TileSideIndex(4)))
        assertTrue(neighbors.hasRoadIdOn(TileSideIndex(5)))
    }

    @Test
    fun `getNeighborsOn by corner is correct`() {
        val neighbors = TileSides.Builder().addNeighbors(listOf(2, null, null, 0, 5, 12)).build()
        assertEquals(
                listOf(HexTileIndex(12), HexTileIndex(2)),
                neighbors.getNeighborsOn(TileCornerIndex(0)))
        assertEquals(
                listOf(HexTileIndex(2), null),
                neighbors.getNeighborsOn(TileCornerIndex(1)))
        assertEquals(
                listOf(null, null),
                neighbors.getNeighborsOn(TileCornerIndex(2)))
        assertEquals(
                listOf(null, HexTileIndex(0)),
                neighbors.getNeighborsOn(TileCornerIndex(3)))
        assertEquals(
                listOf(HexTileIndex(0), HexTileIndex(5)),
                neighbors.getNeighborsOn(TileCornerIndex(4)))
        assertEquals(
                listOf(HexTileIndex(5), HexTileIndex(12)),
                neighbors.getNeighborsOn(TileCornerIndex(5)))
    }

    @Test
    fun `indexOf by tile side is correct`() {
        val ids = listOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val neighbors = TileSides.Builder()
                .addNeighbors(listOf(2, null, null, 0, 5, 12))
                .addRoads(ids)
                .build()
        assertEquals(TileSideIndex(0), neighbors.indexOf(TileSide(HexTileIndex(2), ids[0])))
        assertEquals(TileSideIndex(3), neighbors.indexOf(TileSide(HexTileIndex(0), ids[3])))
        assertEquals(TileSideIndex(4), neighbors.indexOf(TileSide(HexTileIndex(5), ids[4])))
        assertEquals(TileSideIndex(5), neighbors.indexOf(TileSide(HexTileIndex(12), ids[5])))
        assertEquals(null, neighbors.indexOf(TileSide(HexTileIndex(0), ids[0])))
    }

    @Test
    fun `indexOf by tile index is correct`() {
        val neighbors = TileSides.Builder().addNeighbors(listOf(2, null, null, 0, 5, 12)).build()
        assertEquals(TileSideIndex(0), neighbors.indexOf(HexTileIndex(2)))
        assertEquals(TileSideIndex(3), neighbors.indexOf(HexTileIndex(0)))
        assertEquals(TileSideIndex(4), neighbors.indexOf(HexTileIndex(5)))
        assertEquals(TileSideIndex(5), neighbors.indexOf(HexTileIndex(12)))
        assertEquals(null, neighbors.indexOf(HexTileIndex(13)))
    }

    @Test
    fun `indexOf by road id is correct`() {
        val ids = listOf(UniqueIdentifier(), null, null,
                UniqueIdentifier(), UniqueIdentifier(), UniqueIdentifier())
        val roadIds = TileSides.Builder().addRoads(ids).build()
        assertEquals(TileSideIndex(0), roadIds.indexOf(ids[0]!!))
        assertEquals(TileSideIndex(3), roadIds.indexOf(ids[3]!!))
        assertEquals(TileSideIndex(4), roadIds.indexOf(ids[4]!!))
        assertEquals(TileSideIndex(5), roadIds.indexOf(ids[5]!!))
        assertEquals(null, roadIds.indexOf(UniqueIdentifier()))
    }

    @Test
    fun `getOverlappedCorners is correct`() {
        val neighbors = TileSides.Builder().addNeighbors(listOf(2, null, null, 0, 5, 12)).build()
        assertEquals(
                listOf(
                        AbsoluteCorner(HexTileIndex(12), TileCornerIndex(2)),
                        AbsoluteCorner(HexTileIndex(2), TileCornerIndex(4))
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(0))
        )
        assertEquals(
                listOf(
                        AbsoluteCorner(HexTileIndex(2), TileCornerIndex(3)),
                        null
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(1))
        )
        assertEquals(
                listOf(
                        null,
                        null
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(2))
        )
        assertEquals(
                listOf(
                        null,
                        AbsoluteCorner(HexTileIndex(0), TileCornerIndex(1))
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(3))
        )
        assertEquals(
                listOf(
                        AbsoluteCorner(HexTileIndex(0), TileCornerIndex(0)),
                        AbsoluteCorner(HexTileIndex(5), TileCornerIndex(2))
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(4))
        )
        assertEquals(
                listOf(
                        AbsoluteCorner(HexTileIndex(5), TileCornerIndex(1)),
                        AbsoluteCorner(HexTileIndex(12), TileCornerIndex(3))
                ),
                neighbors.getOverlappedCorners(TileCornerIndex(5))
        )
    }
}