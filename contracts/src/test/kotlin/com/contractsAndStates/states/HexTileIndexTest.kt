package com.contractsAndStates.states

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HexTileIndexTest {

    @Test
    fun `it rejects a negative index`() {
        assertFailsWith<IllegalArgumentException>() {
            HexTileIndex(-1)
        }
    }

    @Test
    fun `it rejects a too large index`() {
        assertFailsWith<IllegalArgumentException>() {
            HexTileIndex(GameBoardState.TILE_COUNT)
        }
    }

    @Test
    fun `value is what you pass to the constructor`() {
        assertEquals(14, HexTileIndex(14).value)
    }
}