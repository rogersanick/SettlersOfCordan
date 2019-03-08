package com.flowTests

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.TurnTrackerState
import com.flows.*
import com.oracleService.flows.DiceRollRequestHandler
import com.oracleService.flows.SignDiceRollHandler
import net.corda.core.contracts.requireThat
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.sdk.token.contracts.states.OwnedTokenAmount
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.MockNodeParameters
import org.junit.After
import org.junit.Before
import org.junit.Test

class ClaimResourcesFlowTest {
    // TODO: Accommodate for random dice rolls so that test correctly verifies every time regardless of random roll return
    private val network = MockNetwork(listOf("com.contractsAndStates", "com.flows", "com.oracleClient", "com.oracleService", "net.corda.sdk.token"),
            notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB"))),
            defaultParameters = MockNetworkParameters(networkParameters = testNetworkParameters(minimumPlatformVersion = 4))
    )
    private val a = network.createNode(MockNodeParameters())
    private val b = network.createNode(MockNodeParameters())
    private val c = network.createNode(MockNodeParameters())
    private val d = network.createNode(MockNodeParameters())
    private val oracleName = CordaX500Name("Oracle", "New York", "US")
    private val oracle = network.createNode(MockNodeParameters(legalName = oracleName))

    @Before
    fun setup() {
        val startedNodes = arrayListOf(a, b, c, d)

        startedNodes.forEach {
            it.registerInitiatedFlow(SetupGameStartFlowResponder::class.java)
            it.registerInitiatedFlow(BuildInitialSettlementFlowResponder::class.java)
            it.registerInitiatedFlow(IssueResourcesFlowResponder::class.java)
        }

        listOf(DiceRollRequestHandler::class.java, SignDiceRollHandler::class.java).forEach { oracle.registerInitiatedFlow(it) }

        network.runNetwork()
    }

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun oracleReturnsARandomDiceRoll() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()
        val oracleParty = oracle.info.chooseIdentity()

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
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayOf(Pair(0,5), Pair(2,5), Pair(4,3), Pair(5,2))
        val nonconflictingHextileIndexAndCoordinatesRound2 = arrayOf(Pair(7,3), Pair(9,3), Pair(11,3), Pair(13,3))


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

        val turnTrackerStateLinearId = arrayOfAllTransactions.last().coreTransaction.outRefsOfType<TurnTrackerState>().first().state.data.linearId
        val gameBoardState = arrayOfAllTransactions.last().coreTransaction.outRefsOfType<GameBoardState>().first().state.data

        val rollDiceFlow = RollDiceFlow(turnTrackerStateLinearId, gameBoardState.copy(), oracleParty)
        val futureWithDiceRoll = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRoll.getOrThrow()

        val futureWithClaimedResources = arrayOfAllPlayerNodesInOrder[0].startFlow(IssueResourcesFlow(gameBoardLinearId = gameBoardState.linearId, oracle = oracleParty))
        network.runNetwork()
        val txWithNewReources = futureWithClaimedResources.getOrThrow()

        requireThat {
          "Assert that resources were produced in the transaction" using txWithNewReources.coreTransaction.outputsOfType<OwnedTokenAmount<*>>().isNotEmpty()
        }
    }

}
