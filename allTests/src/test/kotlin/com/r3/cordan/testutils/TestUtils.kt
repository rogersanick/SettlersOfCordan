package com.r3.cordan.testutils

import net.corda.core.flows.FlowLogic
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode

fun <T> StartedMockNode.runFlowAndReturn(flow : FlowLogic<T>, network: MockNetwork) : T {
    val future = this.startFlow(flow)
    if (network.networkSendManuallyPumped) network.runNetwork()
    return future.getOrThrow()
}