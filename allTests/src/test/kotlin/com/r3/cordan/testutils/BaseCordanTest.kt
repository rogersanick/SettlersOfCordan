package com.r3.cordan.testutils

import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.*
import net.corda.testing.node.internal.TestCordappInternal
import net.corda.testing.node.internal.cordappsForPackages
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class BaseCordanTest {

    val network = MockNetwork(
            MockNetworkParameters(
                threadPerNode = true,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 5),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB"))),
                cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("com.r3.cordan.primary.flows"),
                    TestCordapp.findCordapp("com.r3.cordan.primary.contracts"),
                    TestCordapp.findCordapp("com.r3.cordan.primary.states"),
                    TestCordapp.findCordapp("com.r3.cordan.oracle.client.flows"),
                    TestCordapp.findCordapp("com.r3.cordan.oracle.client.contracts"),
                    TestCordapp.findCordapp("com.r3.cordan.oracle.client.states"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.money")
                ).map { it as TestCordappInternal }
            )
    )

    val a = network.createNode(MockNodeParameters())
    val b = network.createNode(MockNodeParameters())
    val c = network.createNode(MockNodeParameters())
    val d = network.createNode(MockNodeParameters())
    val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
    val notary = network.defaultNotaryNode

    // Get an identity for each of the players of the game.
    val p1 = a.info.chooseIdentity()
    val p2 = b.info.chooseIdentity()
    val p3 = c.info.chooseIdentity()
    val p4 = d.info.chooseIdentity()

    private val oracleName = CordaX500Name("Oracle", "New York", "US")
    val oracle = network.createNode(
            MockNodeParameters(
                    legalName = oracleName,
                    additionalCordapps = listOf(
                            TestCordapp.findCordapp("com.r3.cordan.oracle.service") as TestCordappInternal
                    )
            )
    )

    @BeforeEach
    fun setup() {
        if (network.networkSendManuallyPumped) network.runNetwork()
        network.startNodes()
    }

    @AfterEach
    open fun stop() = network.stopNodes()
}
