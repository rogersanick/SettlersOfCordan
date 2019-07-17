package com.resourceFlowTests

import com.contractsAndStates.states.GameBoardState
import com.flows.*
import com.r3.corda.lib.tokens.contracts.AbstractTokenContract
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.testUtilities.setupGameBoardForTesting
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowException
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class ClaimResourcesFlowTest {
    private val network = MockNetwork(MockNetworkParameters(
            notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB"))),
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
            cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("com.flows"),
                    TestCordapp.findCordapp("com.oracleClientFlows"),
                    TestCordapp.findCordapp("com.contractsAndStates"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.money")
            )
    )
    )
    private val a = network.createNode(MockNodeParameters())
    private val b = network.createNode(MockNodeParameters())
    private val c = network.createNode(MockNodeParameters())
    private val d = network.createNode(MockNodeParameters())
    private val oracleName = CordaX500Name("Oracle", "New York", "US")
    private val oracle = network.createNode(
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
    fun tearDown() = network.stopNodes()

    @Test
    fun player1IsAbleToClaimTheAppropriateResourcesAfterSetup() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger
        val gameStateIssueFlow = (SetupGameBoardFlow(p1, p2, p3, p4))
        val futureWithGameState = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stxGameState = futureWithGameState.getOrThrow()

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()

        val arrayOfAllTransactions = arrayListOf<SignedTransaction>()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d);
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions)

        val gameBoardState = arrayOfAllTransactions.last().coreTransaction.outRefsOfType<GameBoardState>().first().state.data

        val rollDiceFlow = RollDiceFlow(gameBoardState.linearId)
        val futureWithDiceRoll = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRoll.getOrThrow()

        val futureWithClaimedResources = arrayOfAllPlayerNodesInOrder[0].startFlow(GatherResourcesFlow(gameBoardLinearId = gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResources = futureWithClaimedResources.getOrThrow()

        requireThat {
            val resources = txWithNewResources.coreTransaction.outputsOfType<FungibleToken>()
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

        // Issue a game state onto the ledger
        val gameStateIssueFlow = (SetupGameBoardFlow(p1, p2, p3, p4))
        val futureWithGameState = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stxGameState = futureWithGameState.getOrThrow()

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()

        val arrayOfAllTransactions = arrayListOf<SignedTransaction>()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d);
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions)

        val gameBoardState = arrayOfAllTransactions.last().coreTransaction.outRefsOfType<GameBoardState>().first().state.data

        val rollDiceFlow = RollDiceFlow(gameBoardState.linearId)
        val futureWithDiceRollPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRollPlayer1.getOrThrow()

        val futureWithClaimedResourcesByPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(GatherResourcesFlow(gameBoardLinearId = gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer1 = futureWithClaimedResourcesByPlayer1.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer1.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer2Turn = arrayOfAllPlayerNodesInOrder[2].startFlow(EndTurnFlow(gameBoardState.linearId))
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

        // Issue a game state onto the ledger
        val gameStateIssueFlow = (SetupGameBoardFlow(p1, p2, p3, p4))
        val futureWithGameState = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stxGameState = futureWithGameState.getOrThrow()

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()

        val arrayOfAllTransactions = arrayListOf<SignedTransaction>()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d);
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions)

        val gameBoardState = arrayOfAllTransactions.last().coreTransaction.outRefsOfType<GameBoardState>().first().state.data

        val rollDiceFlow = RollDiceFlow(gameBoardState.linearId)
        val futureWithDiceRollPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRollPlayer1.getOrThrow()

        val futureWithClaimedResourcesByPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(GatherResourcesFlow(gameBoardLinearId = gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer1 = futureWithClaimedResourcesByPlayer1.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer1.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer2Turn = arrayOfAllPlayerNodesInOrder[0].startFlow(EndTurnFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer2Turn.getOrThrow()

        val futureWithPlayer2DiceRoll = arrayOfAllPlayerNodesInOrder[1].startFlow(RollDiceFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer2DiceRoll.getOrThrow()

        val futureWithClaimedResourcesByPlayer2 = arrayOfAllPlayerNodesInOrder[1].startFlow(GatherResourcesFlow(gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer2 = futureWithClaimedResourcesByPlayer2.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer2.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer3Turn = arrayOfAllPlayerNodesInOrder[1].startFlow(EndTurnFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer3Turn.getOrThrow()

        val futureWithPlayer3DiceRoll = arrayOfAllPlayerNodesInOrder[2].startFlow(RollDiceFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer3DiceRoll.getOrThrow()

        val futureWithClaimedResourcesByPlayer3 = arrayOfAllPlayerNodesInOrder[2].startFlow(GatherResourcesFlow(gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer3 = futureWithClaimedResourcesByPlayer3.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer3.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer4Turn = arrayOfAllPlayerNodesInOrder[2].startFlow(EndTurnFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer4Turn.getOrThrow()

        val futureWithPlayer4DiceRoll = arrayOfAllPlayerNodesInOrder[3].startFlow(RollDiceFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer4DiceRoll.getOrThrow()

        val futureWithClaimedResourcesByPlayer4 = arrayOfAllPlayerNodesInOrder[3].startFlow(GatherResourcesFlow(gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer4 = futureWithClaimedResourcesByPlayer4.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer4.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

    }

}
