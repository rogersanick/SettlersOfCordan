package net.corda.cordan.flow

import net.corda.cordan.state.GameBoardState
import net.corda.cordan.state.TurnTrackerState
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockNodeParameters
import org.junit.After
import org.junit.Before
import org.junit.Test

class InitialSettlementPlacementFlowTests {
    private val network = MockNetwork(listOf("net.corda.cordan"),
            notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB"))),
            defaultParameters = MockNetworkParameters(networkParameters = testNetworkParameters(minimumPlatformVersion = 4))
    )
    private val a = network.createNode(MockNodeParameters())
    private val b = network.createNode(MockNodeParameters())
    private val c = network.createNode(MockNodeParameters())
    private val d = network.createNode(MockNodeParameters())

    @Before
    fun setup() {
        val startedNodes = arrayListOf(a, b, c, d)
        startedNodes.forEach {
            it.registerInitiatedFlow(SetupGameStartFlowResponder::class.java)
            it.registerInitiatedFlow(BuildInitialSettlementFlowResponder::class.java)
        }
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

        // Issue a game state onto the ledger
        val gameStateIssueFlow = (SetupGameStartFlow(p1, p2, p3, p4))
        val futureWithGameState = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stxGameState = futureWithGameState.getOrThrow()

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()

        // Build an initial settlement by issuing a settlement state
        // and updating the current turn.
        val buildInitialSettlementFlow = BuildInitialSettlementFlow(gameState.linearId, 0, 5)
        val futureWithInitialSettlementBuild = a.startFlow(buildInitialSettlementFlow)
        network.runNetwork()

        val stxBuildInitialSettlement = futureWithInitialSettlementBuild.getOrThrow()

        assert(stxBuildInitialSettlement.tx.inputs.size == 2)
        assert(stxBuildInitialSettlement.tx.outputs.size == 3)

        val buildCommands = stxBuildInitialSettlement.tx.commands

        assert(buildCommands.first().signers.toSet() == stxBuildInitialSettlement.tx.outputsOfType<TurnTrackerState>().single().participants.map { it.owningKey }.toSet())

        stxBuildInitialSettlement.verifyRequiredSignatures()
    }

    @Test
    fun concurrentFlowsAreAbleToPlaceAllPieces() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger
        val gameStateIssueFlow = (SetupGameStartFlow(p1, p2, p3, p4))
        val futureWithGameState = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stxGameState = futureWithGameState.getOrThrow()

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()

        val arrayOfAllTransactions = arrayListOf<SignedTransaction>()
        val arrayOfAllPlayers = arrayListOf(a, b, c, d)

        fun placeAPieceFromASpecificNode(i: Int) {
            // Build an initial settlement by issuing a settlement state
            // and updating the current turn.
            val buildInitialSettlementFlow = BuildInitialSettlementFlow(gameState.linearId, 0, 5)
            val currPlayer = arrayOfAllPlayers[i]
            val futureWithInitialSettlementBuild = currPlayer.startFlow(buildInitialSettlementFlow)
            network.runNetwork()
            arrayOfAllTransactions.add(futureWithInitialSettlementBuild.getOrThrow())
        }

        for (i in 0..3) {
            placeAPieceFromASpecificNode(i)
        }

        for (i in 3.downTo(0)) {
            placeAPieceFromASpecificNode(i)
        }

        for (i in arrayOfAllTransactions) {
            System.out.println("This is placement ${i.coreTransaction.outputsOfType<TurnTrackerState>().first().currTurnIndex}")
            System.out.println(i.coreTransaction.outputsOfType<TurnTrackerState>().first().setUpRound1Complete)
            System.out.println(i.coreTransaction.outputsOfType<TurnTrackerState>().first().setUpRound1Complete)
        }


    }

}
