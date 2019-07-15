package com.contractsAndStates.states

import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlacedPortsTest {

    @Test
    fun `Constructor accepts proper input`() {
        @Suppress("RemoveExplicitTypeArguments") // Otherwise compiler fails
        val placedPorts = PlacedPorts(List<Port>(PlacedPorts.PORT_COUNT) { mock() })
    }

    @Test
    fun `Constructor rejects too short list`() {
        assertFailsWith<IllegalArgumentException> {
            @Suppress("RemoveExplicitTypeArguments") // Otherwise compiler fails
            PlacedPorts(List<Port>(PlacedPorts.PORT_COUNT - 1) { mock() })
        }
    }

    @Test
    fun `Constructor rejects too long list`() {
        assertFailsWith<IllegalArgumentException> {
            @Suppress("RemoveExplicitTypeArguments") // Otherwise compiler fails
            PlacedPorts(List<Port>(PlacedPorts.PORT_COUNT + 1) { mock() })
        }
    }

    @Test
    fun `Builder-build accepts proper input`() {
        val builder = PlacedPorts.Builder()
        for (i in 0 until PlacedPorts.PORT_COUNT) {
            builder.add(mock<PortTile>())
                    .add(listOf(mock()))
        }
        assertEquals(PlacedPorts.PORT_COUNT, builder.build().value.size)
    }

    @Test
    fun `Builder-add rejects empty list`() {
        val builder = PlacedPorts.Builder()
        assertFailsWith<IllegalArgumentException> {
            builder.add(listOf())
        }
    }

    @Test
    fun `Builder-build rejects if unequal lengths`() {
        val builder = PlacedPorts.Builder()
        for (i in 0 until PlacedPorts.PORT_COUNT-1) {
            builder.add(mock<PortTile>())
                    .add(listOf(mock()))
        }
        builder.add(mock<PortTile>())
        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }
}