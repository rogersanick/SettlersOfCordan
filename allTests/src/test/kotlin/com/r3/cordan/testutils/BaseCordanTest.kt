package com.r3.cordan.testutils

import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach

abstract class BaseCordanTest {

    var network = MockNetwork(MockNetworkParameters(
            notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB"))),
            networkParameters = testNetworkParameters(minimumPlatformVersion = 5),
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
            )))

    var a = network.createNode(MockNodeParameters())
    var b = network.createNode(MockNodeParameters())
    var c = network.createNode(MockNodeParameters())
    var d = network.createNode(MockNodeParameters())

    // Get an identity for each of the players of the game.
    var p1 = a.info.chooseIdentity()
    var p2 = b.info.chooseIdentity()
    var p3 = c.info.chooseIdentity()
    var p4 = d.info.chooseIdentity()

    private val oracleName = CordaX500Name("Oracle", "New York", "US")
    val oracle = network.createNode(
            MockNodeParameters(legalName = oracleName).withAdditionalCordapps(
                    listOf(
                            TestCordapp.findCordapp("com.r3.cordan.oracle.service")
                    )
            )
    )

    @BeforeEach
    fun setup() {
        network.runNetwork()
    }

    @AfterEach
    open fun tearDown() {
        network.stopNodes()
    }

}
