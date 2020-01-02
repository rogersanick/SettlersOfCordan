package com.r3.cordan.primary.states

import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.cordan.primary.states.board.PortTile
import com.r3.cordan.primary.states.resources.Ore
import com.r3.cordan.primary.states.resources.Sheep
import com.r3.cordan.primary.states.resources.allResources
import com.r3.cordan.primary.states.resources.mapOf
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PortTileTest {

    @Test
    fun `Constructor accepts proper input`() {
        val tile = PortTile(listOf(1 of Sheep), listOf(2 of Ore))
        assertEquals(1 of Sheep, tile.inputRequired[0])
        assertEquals(2 of Ore, tile.outputRequired[0])
    }

    @Test
    fun `Constructor rejects empty inputRequired`() {
        assertFailsWith<IllegalArgumentException> {
            PortTile(listOf(), listOf(2 of Ore))
        }
    }

    @Test
    fun `Constructor rejects empty outputRequired`() {
        assertFailsWith<IllegalArgumentException> {
            PortTile(listOf(1 of Sheep), listOf())
        }
    }

    @Test
    fun `Constructor rejects duplicates in inputRequired`() {
        assertFailsWith<IllegalArgumentException> {
            PortTile(listOf(1 of Sheep, 2 of Sheep), listOf(2 of Ore))
        }
    }

    @Test
    fun `Constructor rejects duplicates in outputRequired`() {
        assertFailsWith<IllegalArgumentException> {
            PortTile(listOf(1 of Sheep), listOf(1 of Ore, 2 of Ore))
        }
    }

    @Test
    fun `Constructor rejects 0 amount in inputRequired`() {
        assertFailsWith<IllegalArgumentException> {
            PortTile(listOf(0 of Sheep), listOf(2 of Ore))
        }
    }

    @Test
    fun `Constructor rejects 0 amount in outputRequired`() {
        assertFailsWith<IllegalArgumentException> {
            PortTile(listOf(1 of Sheep), listOf(0 of Ore))
        }
    }

    @Test
    fun `getInputOf is correct`() {
        val tile = PortTile(listOf(Sheep).mapOf(2), allResources.minus(Sheep).mapOf(1))
        assertEquals(2 of Sheep, tile.getInputOf(Sheep))
    }

    @Test
    fun `getInputOf rejects missing type`() {
        val tile = PortTile(listOf(Sheep).mapOf(2), allResources.minus(Sheep).mapOf(1))
        assertFailsWith<NoSuchElementException> {
            tile.getInputOf(Ore)
        }
    }

    @Test
    fun `getOutputOf is correct`() {
        val tile = PortTile(listOf(Sheep).mapOf(2), allResources.minus(Sheep).mapOf(1))
        assertEquals(1 of Ore, tile.getOutputOf(Ore))
    }

    @Test
    fun `getOutputOf rejects missing type`() {
        val tile = PortTile(listOf(Sheep).mapOf(2), allResources.minus(Sheep).mapOf(1))
        assertFailsWith<NoSuchElementException> {
            tile.getOutputOf(Sheep)
        }
    }
}