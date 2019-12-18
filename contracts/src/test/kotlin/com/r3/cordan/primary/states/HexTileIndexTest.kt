package com.r3.cordan.primary.states

import com.r3.cordan.primary.states.board.HexTileIndex
import com.r3.cordan.primary.states.structure.GameBoardState
import org.junit.jupiter.api.Test
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