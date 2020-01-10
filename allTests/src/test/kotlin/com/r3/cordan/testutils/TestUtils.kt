package com.r3.cordan.testutils

import net.corda.core.flows.FlowLogic
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.InternalMockNetwork

fun <T> StartedMockNode.runFlowAndReturn(flow : FlowLogic<T>, network: InternalMockNetwork) : T {
    val future = this.startFlow(flow)
    network.runNetwork()
    return future.getOrThrow()
}