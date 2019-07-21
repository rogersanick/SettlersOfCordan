package com.contractsAndStates.states

import com.oracleClientStatesAndContracts.states.RollTrigger
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HexTileTest {

    private fun makeBasicBuilder(tileIndex: HexTileIndex) = HexTile.Builder(tileIndex)
            .with(HexTileType.Desert)
            .with(RollTrigger(3))
            .with(true)

    @Test
    fun `getAllSides is correct`() {
        assertEquals(listOf(TileSideIndex(0), TileSideIndex(1), TileSideIndex(2),
                TileSideIndex(3), TileSideIndex(4), TileSideIndex(5)),
                HexTile.getAllSides())
    }

    @Test
    fun `getAllCorners is correct`() {
        assertEquals(listOf(TileCornerIndex(0), TileCornerIndex(1), TileCornerIndex(2),
                TileCornerIndex(3), TileCornerIndex(4), TileCornerIndex(5)),
                HexTile.getAllCorners())
    }

    @Test
    fun `Builder rejects replacing resourceType`() {
        val builder = makeBasicBuilder(HexTileIndex(1))
        assertFailsWith<IllegalArgumentException> {
            builder.with(HexTileType.Forest)
        }
    }

    @Test
    fun `Builder accepts setting resourceType to the same`() {
        val builder = makeBasicBuilder(HexTileIndex(1))
                .with(HexTileType.Desert)
        assertEquals(HexTileType.Desert, builder.resourceType)
    }

    @Test
    fun `Builder rejects replacing roleTrigger`() {
        val builder = makeBasicBuilder(HexTileIndex(1))
        assertFailsWith<IllegalArgumentException> {
            builder.with(RollTrigger(2))
        }
    }

    @Test
    fun `Builder rejects putting roleTrigger back to null`() {
        val builder = makeBasicBuilder(HexTileIndex(1))
        assertFailsWith<IllegalArgumentException> {
            builder.with(null)
        }
    }

    @Test
    fun `Builder accepts setting roleTrigger to the same`() {
        val builder = makeBasicBuilder(HexTileIndex(1))
                .with(RollTrigger(3))
        assertEquals(RollTrigger(3), builder.rollTrigger)
    }

    @Test
    fun `Builder accepts setting roleTrigger to null`() {
        val builder = HexTile.Builder(HexTileIndex(1))
                .with(HexTileType.Desert)
                .with(null)
                .with(true)

        assertEquals(null, builder.rollTrigger)
    }

    @Test
    fun `Builder rejects replacing robberPresent`() {
        val builder = makeBasicBuilder(HexTileIndex(1))
        assertFailsWith<IllegalArgumentException> {
            builder.with(false)
        }
    }

    @Test
    fun `Builder accepts setting robberPresent to the same`() {
        val builder = makeBasicBuilder(HexTileIndex(1))
                .with(true)
        assertTrue(builder.robberPresent!!)
    }

    @Test
    fun `Builder-build is correct`() {
        val built = makeBasicBuilder(HexTileIndex(4)).build()
        assertEquals(HexTileType.Desert, built.resourceType)
        assertEquals(RollTrigger(3), built.rollTrigger)
        assertTrue(built.robberPresent)
        assertEquals(HexTileIndex(4), built.hexTileIndex)
        // TODO test roads?
    }

    @Test
    fun `connect is correct 1-5`() {
        val builder1 = makeBasicBuilder(HexTileIndex(1))
        val builder5 = makeBasicBuilder(HexTileIndex(5))
        assertFalse(builder1.isNeighborOn(TileSideIndex(3), builder5.hexTileIndex))
        assertFalse(builder5.isNeighborOn(TileSideIndex(0), builder1.hexTileIndex))

        builder5.connect(TileSideIndex(0), builder1)
        assertTrue(builder1.isNeighborOn(TileSideIndex(3), builder5.hexTileIndex))
        assertTrue(builder5.isNeighborOn(TileSideIndex(0), builder1.hexTileIndex))
    }

    @Test
    fun `build keeps connection 1-5`() {
        val builder1 = makeBasicBuilder(HexTileIndex(1))
        val builder5 = makeBasicBuilder(HexTileIndex(5))

        val tile5 = builder5.connect(TileSideIndex(0), builder1).build()
        assertEquals(
                HexTileIndex(1),
                tile5.sides.getNeighborOn(TileSideIndex(0)))

        val tile1 = builder1.build()
        assertEquals(
                HexTileIndex(5),
                tile1.sides.getNeighborOn(TileSideIndex(3)))
    }
}
