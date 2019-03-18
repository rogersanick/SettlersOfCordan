package com.flowTests

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.TurnTrackerState
import com.flows.BuildInitialSettlementFlow
import com.flows.BuildInitialSettlementFlowResponder
import com.flows.SetupGameStartFlow
import com.flows.SetupGameStartFlowResponder
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
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
    private val network = MockNetwork(listOf("com.contractsAndStates", "com.flows", "net.corda.sdk.token"),
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
    fun flowReturnsCorrectlyFormedFullySignedTransaction() {

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
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d);
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }
        val futureWithInitialSettlementBuild = arrayOfAllPlayerNodesInOrder.first().startFlow(buildInitialSettlementFlow)
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
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d);
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayOf(Pair(0,5), Pair(0,1), Pair(0,3), Pair(1,1))
        val nonconflictingHextileIndexAndCoordinatesRound2 = arrayOf(Pair(1,3), Pair(2,1), Pair(2,3), Pair(3,3))


        fun placeAPieceFromASpecificNode(i: Int, testCoordinates: Array<Pair<Int, Int>>) {
            // Build an initial settlement by issuing a settlement state
            // and updating the current turn.
            val buildInitialSettlementFlow = BuildInitialSettlementFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second)
            val currPlayer = arrayOfAllPlayerNodesInOrder[i]
            val futureWithInitialSettlementBuild = currPlayer.startFlow(buildInitialSettlementFlow)
            network.runNetwork()
            arrayOfAllTransactions.add(futureWithInitialSettlementBuild.getOrThrow())
        }

        for (i in 0..3) {
            placeAPieceFromASpecificNode(i, nonconflictingHextileIndexAndCoordinatesRound1)
        }

        for (i in 3.downTo(0)) {
            placeAPieceFromASpecificNode(i, nonconflictingHextileIndexAndCoordinatesRound2)
        }

    }

    @Test(expected = TransactionVerificationException::class)
    fun concurrentFlowsAreUnableToPlaceInvalidPieces() {

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
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d);
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayOf(Pair(0,5), Pair(0,4), Pair(0,3), Pair(1,1))
        val nonconflictingHextileIndexAndCoordinatesRound2 = arrayOf(Pair(1,3), Pair(2,1), Pair(2,3), Pair(3,3))


        fun placeAPieceFromASpecificNode(i: Int, testCoordinates: Array<Pair<Int, Int>>) {
            // Build an initial settlement by issuing a settlement state
            // and updating the current turn.
            val buildInitialSettlementFlow = BuildInitialSettlementFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second)
            val currPlayer = arrayOfAllPlayerNodesInOrder[i]
            val futureWithInitialSettlementBuild = currPlayer.startFlow(buildInitialSettlementFlow)
            network.runNetwork()
            arrayOfAllTransactions.add(futureWithInitialSettlementBuild.getOrThrow())
        }

        for (i in 0..3) {
            placeAPieceFromASpecificNode(i, nonconflictingHextileIndexAndCoordinatesRound1)
        }

        for (i in 3.downTo(0)) {
            placeAPieceFromASpecificNode(i, nonconflictingHextileIndexAndCoordinatesRound2)
        }

    }

    @Test(expected = Error::class)
    fun playersAreUnableToBuildSettlementUsingThePlaceInitialSettlementFlowAfterSetup() {

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
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d);
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayOf(Pair(0,5), Pair(0,1), Pair(0,3), Pair(1,1))
        val nonconflictingHextileIndexAndCoordinatesRound2 = arrayOf(Pair(1,3), Pair(2,1), Pair(2,3), Pair(3,3))


        fun placeAPieceFromASpecificNode(i: Int, testCoordinates: Array<Pair<Int, Int>>) {
            // Build an initial settlement by issuing a settlement state
            // and updating the current turn.
            val buildInitialSettlementFlow = BuildInitialSettlementFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second)
            val currPlayer = arrayOfAllPlayerNodesInOrder[i]
            val futureWithInitialSettlementBuild = currPlayer.startFlow(buildInitialSettlementFlow)
            network.runNetwork()
            arrayOfAllTransactions.add(futureWithInitialSettlementBuild.getOrThrow())
        }

        for (i in 0..3) {
            placeAPieceFromASpecificNode(i, nonconflictingHextileIndexAndCoordinatesRound1)
        }

        for (i in 3.downTo(0)) {
            placeAPieceFromASpecificNode(i, nonconflictingHextileIndexAndCoordinatesRound2)
        }

        placeAPieceFromASpecificNode(0, arrayOf(Pair(9, 3)))

    }

    @Test(expected = FlowException::class)
    fun playersMustBuildSettlementsAccordingToTheTurnOrder() {

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
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d);
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayOf(Pair(0,5), Pair(0,1), Pair(0,3), Pair(1,1))


        fun placeAPieceFromASpecificNode(i: Int, testCoordinates: Array<Pair<Int, Int>>) {
            // Build an initial settlement by issuing a settlement state
            // and updating the current turn.
            val buildInitialSettlementFlow = BuildInitialSettlementFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second)
            val currPlayer = arrayOfAllPlayerNodesInOrder[i]
            val futureWithInitialSettlementBuild = currPlayer.startFlow(buildInitialSettlementFlow)
            network.runNetwork()
            arrayOfAllTransactions.add(futureWithInitialSettlementBuild.getOrThrow())
        }

        placeAPieceFromASpecificNode(0, nonconflictingHextileIndexAndCoordinatesRound1)
        placeAPieceFromASpecificNode(2, nonconflictingHextileIndexAndCoordinatesRound1)

    }

}
