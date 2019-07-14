package com.contractsAndStates.states

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HexTileTest {

    private fun makeBasicBuilder(tileIndex: HexTileIndex) = HexTile.Builder(tileIndex)
            .with(HexTileType.Desert)
            .with(1)
            .with(true)

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
            builder.with(2)
        }
    }

    @Test
    fun `Builder accepts setting roleTrigger to the same`() {
        val builder = makeBasicBuilder(HexTileIndex(1))
                .with(1)
        assertEquals(1, builder.roleTrigger)
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
        assertEquals(1, built.roleTrigger)
        assertTrue(built.robberPresent)
        assertEquals(HexTileIndex(4), built.hexTileIndex)
        // TODO test roads?
    }

    @Test
    fun `connect is correct 1-5`() {
        val builder1 = makeBasicBuilder(HexTileIndex(1))
        val builder5 = makeBasicBuilder(HexTileIndex(5))
        assertFalse(builder1.isConnectedWith(TileSideIndex(3), builder5.hexTileIndex))
        assertFalse(builder5.isConnectedWith(TileSideIndex(0), builder1.hexTileIndex))

        builder5.connect(TileSideIndex(0), builder1)
        assertTrue(builder1.isConnectedWith(TileSideIndex(3), builder5.hexTileIndex))
        assertTrue(builder5.isConnectedWith(TileSideIndex(0), builder1.hexTileIndex))
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
