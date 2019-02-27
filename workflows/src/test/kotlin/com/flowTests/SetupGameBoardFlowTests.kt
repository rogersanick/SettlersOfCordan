package com.flowTests

import com.contractsAndStates.contracts.GameStateContract
import com.contractsAndStates.states.GameBoardState
import com.flows.SetupGameStartFlow
import com.flows.SetupGameStartFlowResponder
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNodeParameters
import org.junit.After
import org.junit.Before
import org.junit.Test

class SetupGameBoardFlowTests {
    private val network = MockNetwork(listOf("com.contractsAndStates", "com.flows"), notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB"))))
    private val a = network.createNode(MockNodeParameters())
    private val b = network.createNode(MockNodeParameters())
    private val c = network.createNode(MockNodeParameters())
    private val d = network.createNode(MockNodeParameters())
    private val e = network.createNode(MockNodeParameters())
    private val f = network.createNode(MockNodeParameters())

    @Before
    fun setup() {
        val startedNodes = arrayListOf(a, b, c, d, e, f)
        // For real nodes this happens automatically, but we have to manually register the flow for tests
        startedNodes.forEach { it.registerInitiatedFlow(SetupGameStartFlowResponder::class.java) }
        network.runNetwork()
    }

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Get an identity for two additional spectators in the game.
//        val spec1 = e.info.chooseIdentity()
//        val spec2 = f.info.chooseIdentity()

        // Issue a game state onto the ledger.
        val gameStateIssueFlow = SetupGameStartFlow(p1, p2, p3, p4)
        val future = a.startFlow(gameStateIssueFlow);
        network.runNetwork()

        val ptx = future.getOrThrow()

        assert(ptx.tx.inputs.isEmpty())
        assert(ptx.tx.outputs.single().data is GameBoardState)

        // This is a change for git.
        val command = ptx.tx.commands.single()

        assert(command.value is GameStateContract.Commands.SetUpGameBoard)
        assert(command.signers.toSet() == ptx.tx.outputs.single().data.participants.map { it.owningKey }.toSet())

        ptx.verifySignaturesExcept(
                p2.owningKey,
                p3.owningKey,
                p4.owningKey,
                network.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey
        )
    }

    @Test
    fun flowReturnsCorrectlyFormedSignedTransaction() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Get an identity for two additional spectators in the game.
//        val spec1 = e.info.chooseIdentity()
//        val spec2 = f.info.chooseIdentity()

        // Issue a game state onto the ledger.
        val gameStateIssueFlow = SetupGameStartFlow(p1, p2, p3, p4)
        val future = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stx = future.getOrThrow()

        assert(stx.tx.inputs.isEmpty())
        assert(stx.tx.outputs.single().data is GameBoardState)

        val command = stx.tx.commands.single()

        assert(command.value is GameStateContract.Commands.SetUpGameBoard)
        assert(command.signers.toSet() == stx.tx.outputs.single().data.participants.map { it.owningKey }.toSet())

        stx.verifyRequiredSignatures()
    }
}