package com.contractsAndStates.states

import org.junit.Test
import kotlin.test.assertFailsWith

class TileRoadIdsTest {

    @Test
    fun `it rejects a too short list`() {
        assertFailsWith<IllegalArgumentException>() {
            TileRoadIds(List(HexTile.SIDE_COUNT - 1) { null })
        }
    }

    @Test
    fun `it rejects a too large index`() {
        assertFailsWith<IllegalArgumentException>() {
            TileRoadIds(List(HexTile.SIDE_COUNT + 1) { null })
        }
    }
}