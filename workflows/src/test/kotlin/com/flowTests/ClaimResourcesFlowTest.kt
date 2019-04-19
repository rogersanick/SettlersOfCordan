package com.flowTests

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.TurnTrackerState
import com.flows.*
import com.oracleService.flows.DiceRollRequestHandler
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.requireThat
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
import kotlin.test.assertFailsWith

class ClaimResourcesFlowTest {
    private val network = MockNetwork(listOf("com.contractsAndStates", "com.flows", "com.oracleClient", "com.oracleService", "com.r3.corda.sdk.token.workflows", "com.r3.corda.sdk.token.contracts", "com.r3.corda.sdk.token.money"),
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

        listOf(DiceRollRequestHandler::class.java).forEach { oracle.registerInitiatedFlow(it) }

        network.runNetwork()
    }

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun player1IsAbleToClaimTheAppropriateResourcesAfterSetup() {

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
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayListOf(Pair(0,5), Pair(1,5), Pair(2,5), Pair(3,5))
        val nonconflictingHextileIndexAndCoordinatesRound2 = arrayListOf(Pair(4,5), Pair(5,5), Pair(6,5), Pair(7,5))


        fun placeAPieceFromASpecificNode(i: Int, testCoordinates: ArrayList<Pair<Int, Int>>) {
            // Build an initial settlement by issuing a settlement state
            // and updating the current turn.
            if (gameState.hexTiles[testCoordinates[i].first].resourceType == "Desert") {
                testCoordinates[i] = Pair(testCoordinates[i].first + 7, testCoordinates[i].second)
            }
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

        val gameBoardState = arrayOfAllTransactions.last().coreTransaction.outRefsOfType<GameBoardState>().first().state.data

        val rollDiceFlow = RollDiceFlow(gameBoardState.linearId)
        val futureWithDiceRoll = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRoll.getOrThrow()

        val futureWithClaimedResources = arrayOfAllPlayerNodesInOrder[0].startFlow(IssueResourcesFlow(gameBoardLinearId = gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResources = futureWithClaimedResources.getOrThrow()

        requireThat {
            val resources = txWithNewResources.coreTransaction.outputsOfType<FungibleToken<*>>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }
    }

    @Test
    fun playersMustClaimResourcesOnTheirTurn() {

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
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayListOf(Pair(0,5), Pair(1,5), Pair(2,5), Pair(3,5))
        val nonconflictingHextileIndexAndCoordinatesRound2 = arrayListOf(Pair(4,5), Pair(5,5), Pair(6,5), Pair(7,5))


        fun placeAPieceFromASpecificNode(i: Int, testCoordinates: ArrayList<Pair<Int, Int>>) {
            // Build an initial settlement by issuing a settlement state
            // and updating the current turn.
            if (gameState.hexTiles[testCoordinates[i].first].resourceType == "Desert") {
                testCoordinates[i] = Pair(testCoordinates[i].first + 7, testCoordinates[i].second)
            }
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

        val rollDiceFlow = RollDiceFlow(gameBoardState.linearId)
        val futureWithDiceRollPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRollPlayer1.getOrThrow()

        val futureWithClaimedResourcesByPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(IssueResourcesFlow(gameBoardLinearId = gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer1 = futureWithClaimedResourcesByPlayer1.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer1.coreTransaction.outputsOfType<FungibleToken<*>>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer2Turn = arrayOfAllPlayerNodes[0].startFlow(EndTurnFlow())
        network.runNetwork()
        assertFailsWith<FlowException>("The player who is proposing this transaction is not currently the player whose turn it is.") { futureWithPlayer2Turn.getOrThrow() }

    }

    @Test
    fun player1and2AreAbleToClaimTheAppropriateResourcesAfterSetup() {

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
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayListOf(Pair(0,5), Pair(1,5), Pair(2,5), Pair(3,5))
        val nonconflictingHextileIndexAndCoordinatesRound2 = arrayListOf(Pair(4,5), Pair(5,5), Pair(6,5), Pair(7,5))


        fun placeAPieceFromASpecificNode(i: Int, testCoordinates: ArrayList<Pair<Int, Int>>) {
            // Build an initial settlement by issuing a settlement state
            // and updating the current turn.
            if (gameState.hexTiles[testCoordinates[i].first].resourceType == "Desert") {
                testCoordinates[i] = Pair(testCoordinates[i].first + 7, testCoordinates[i].second)
            }
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

        val rollDiceFlow = RollDiceFlow(gameBoardState.linearId)
        val futureWithDiceRollPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRollPlayer1.getOrThrow()

        val futureWithClaimedResourcesByPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(IssueResourcesFlow(gameBoardLinearId = gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer1 = futureWithClaimedResourcesByPlayer1.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer1.coreTransaction.outputsOfType<FungibleToken<*>>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer2Turn = arrayOfAllPlayerNodesInOrder[0].startFlow(EndTurnFlow())
        network.runNetwork()
        futureWithPlayer2Turn.getOrThrow()

        val futureWithPlayer2DiceRoll = arrayOfAllPlayerNodesInOrder[1].startFlow(RollDiceFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer2DiceRoll.getOrThrow()

        val futureWithClaimedResourcesByPlayer2 = arrayOfAllPlayerNodesInOrder[1].startFlow(IssueResourcesFlow(gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer2 = futureWithClaimedResourcesByPlayer2.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer2.coreTransaction.outputsOfType<FungibleToken<TokenType>>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer3Turn = arrayOfAllPlayerNodesInOrder[1].startFlow(EndTurnFlow())
        network.runNetwork()
        futureWithPlayer3Turn.getOrThrow()

        val futureWithPlayer3DiceRoll = arrayOfAllPlayerNodesInOrder[2].startFlow(RollDiceFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer3DiceRoll.getOrThrow()

        val futureWithClaimedResourcesByPlayer3 = arrayOfAllPlayerNodesInOrder[2].startFlow(IssueResourcesFlow(gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer3 = futureWithClaimedResourcesByPlayer3.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer3.coreTransaction.outputsOfType<FungibleToken<TokenType>>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer4Turn = arrayOfAllPlayerNodesInOrder[2].startFlow(EndTurnFlow())
        network.runNetwork()
        futureWithPlayer4Turn.getOrThrow()

        val futureWithPlayer4DiceRoll = arrayOfAllPlayerNodesInOrder[3].startFlow(RollDiceFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer4DiceRoll.getOrThrow()

        val futureWithClaimedResourcesByPlayer4 = arrayOfAllPlayerNodesInOrder[3].startFlow(IssueResourcesFlow(gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer4 = futureWithClaimedResourcesByPlayer4.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer4.coreTransaction.outputsOfType<FungibleToken<TokenType>>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

    }

}
