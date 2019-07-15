package com.contractsAndStates.states

import com.r3.corda.lib.tokens.contracts.utilities.of
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PortTest {

    @Test
    fun `Constructor accepts proper input`() {
        val tile = PortTile(listOf(1 of Sheep), listOf(2 of Ore))
        val accessPoint = AccessPoint(1, listOf(2, 3))
        val port = Port(tile, listOf(accessPoint))
        assertEquals(tile, port.portTile)
        assertEquals(listOf(accessPoint), port.accessPoints)
    }

    @Test
    fun `Constructor rejects empty accessPoints`() {
        val tile = PortTile(listOf(1 of Sheep), listOf(2 of Ore))
        assertFailsWith<IllegalArgumentException> {
            Port(tile, listOf())
        }
    }
}