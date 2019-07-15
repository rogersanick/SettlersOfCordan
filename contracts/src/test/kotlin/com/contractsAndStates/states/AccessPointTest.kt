package com.contractsAndStates.states

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccessPointTest {

    @Test
    fun `Constructor accepts proper input`() {
        val accessPoint = AccessPoint(HexTileIndex(1), listOf(TileCornerIndex(0)))
        assertEquals(HexTileIndex(1), accessPoint.hexTileIndex)
        assertEquals(1, accessPoint.hexTileCoordinate.size)
        assertEquals(TileCornerIndex(0), accessPoint.hexTileCoordinate[0])
    }

    @Test
    fun `Constructor rejects empty hexTileCoordinate`() {
        assertFailsWith<IllegalArgumentException> {
            AccessPoint(HexTileIndex(1), listOf())
        }
    }

    @Test
    fun `Second constructor is correct`() {
        val accessPoint = AccessPoint(1, listOf(0))
        assertEquals(HexTileIndex(1), accessPoint.hexTileIndex)
        assertEquals(1, accessPoint.hexTileCoordinate.size)
        assertEquals(TileCornerIndex(0), accessPoint.hexTileCoordinate[0])
    }
}