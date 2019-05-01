package com.flowTests

import com.contractsAndStates.contracts.GameStateContract
import com.contractsAndStates.states.GameBoardState
import com.flows.SetupGameBoardFlow
import com.flows.SetupGameBoardFlowResponder
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
        startedNodes.forEach { it.registerInitiatedFlow(SetupGameBoardFlowResponder::class.java) }
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

        // Issue a game state onto the ledger.
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val future = a.startFlow(gameStateIssueFlow);
        network.runNetwork()

        val ptx = future.getOrThrow()

        assert(ptx.tx.inputs.isEmpty())

        val gameBoardState = ptx.tx.outputs.filter { it.data is GameBoardState }.single().data
        assert(gameBoardState is GameBoardState)

        val command = ptx.tx.commands.filter { it.value is GameStateContract.Commands.SetUpGameBoard }.single()

        assert(command.value is GameStateContract.Commands.SetUpGameBoard)
        assert(command.signers.toSet() == gameBoardState.participants.map { it.owningKey }.toSet())

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

        // Issue a game state onto the ledger.
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val future = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stx = future.getOrThrow()

        assert(stx.tx.inputs.isEmpty())

        val gameBoardState = stx.tx.outputs.filter { it.data is GameBoardState }.single().data
        assert(gameBoardState is GameBoardState)

        val command = stx.tx.commands.filter { it.value is GameStateContract.Commands.SetUpGameBoard }.single()

        assert(command.value is GameStateContract.Commands.SetUpGameBoard)
        assert(command.signers.toSet() == gameBoardState.participants.map { it.owningKey }.toSet())

        stx.verifyRequiredSignatures()
    }
}