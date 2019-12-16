package com.testUtilities

import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

abstract class BaseCordanTest {

    @get:Rule
    val timeoutRule = Timeout(3, TimeUnit.MINUTES)

    var network = MockNetwork(MockNetworkParameters(
            notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB"))),
            networkParameters = testNetworkParameters(minimumPlatformVersion = 5),
            cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("com.flows"),
                    TestCordapp.findCordapp("com.oracleClientFlows"),
                    TestCordapp.findCordapp("com.contractsAndStates"),
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
                            TestCordapp.findCordapp("com.oracleService")
                    )
            )
    )

    @Before
    fun setup() {
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

}
