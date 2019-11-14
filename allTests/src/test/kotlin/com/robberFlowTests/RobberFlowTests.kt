package com.robberFlowTests

import com.contractsAndStates.states.*
import com.flows.*
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.testUtilities.*
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.requireThat
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class RobberFlowTests {
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
    fun player1IsUnableToMoveTheRobberWhenA7IsNotRolled() {

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
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions)
        val gameBoardState = arrayOfAllTransactions.last().coreTransaction.outRefsOfType<GameBoardState>().first().state.data

        val deterministicDiceRoll = getDiceRollWithSpecifiedRollValue(3, 2, gameBoardState, oracle)
        val rollDiceFlow = RollDiceFlow(gameBoardState.linearId, deterministicDiceRoll)
        val futureWithDiceRoll = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRoll.getOrThrow()

        val futureWithMovedRobber = arrayOfAllPlayerNodesInOrder[0].startFlow(MoveRobberFlow(gameBoardState.linearId, 5))
        network.runNetwork()

        assertFailsWith<TransactionVerificationException.ContractRejection>("The associated dice roll must have a value of 7.") { futureWithMovedRobber.getOrThrow() }
    }

    @Test
    fun player1IsAbleToMoveTheRobberWhenA7IsRolled() {

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
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions)

        val gameBoardState = arrayOfAllTransactions.last().coreTransaction.outRefsOfType<GameBoardState>().first().state.data

        val deterministicDiceRoll = getDiceRollWithSpecifiedRollValue(3, 4, gameBoardState, oracle)
        val rollDiceFlow = RollDiceFlow(gameBoardState.linearId, deterministicDiceRoll)
        val futureWithDiceRoll = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRoll.getOrThrow()

        val futureWithClaimedResources = arrayOfAllPlayerNodesInOrder[0].startFlow(MoveRobberFlow(gameBoardState.linearId, 5))
        network.runNetwork()
        val futureWithMovedRobber = futureWithClaimedResources.getOrThrow()

        requireThat {
            val outputRobber = futureWithMovedRobber.coreTransaction.outputsOfType<RobberState>().first()
            "Assert that the robber has been moved to the appropriate position" using (outputRobber.hexTileIndex == HexTileIndex(5))
        }
    }

    @Test
    fun player1IsAbleToApplyTheRobberAfterMovingIt() {

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
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions)

        val gameBoardState = arrayOfAllTransactions.last().coreTransaction.outRefsOfType<GameBoardState>().first().state.data

        val deterministicDiceRoll = getDiceRollWithSpecifiedRollValue(3, 4, gameBoardState, oracle)
        val rollDiceFlow = RollDiceFlow(gameBoardState.linearId, deterministicDiceRoll)
        val futureWithDiceRoll = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRoll.getOrThrow()

        val futureWithMovedRobber = arrayOfAllPlayerNodesInOrder[0].startFlow(MoveRobberFlow(gameBoardState.linearId, 5))
        network.runNetwork()
        futureWithMovedRobber.getOrThrow()

        val futureWithRobberApplied = arrayOfAllPlayerNodesInOrder[0].startFlow(ApplyRobberFlow(gameBoardState.linearId))
        network.runNetwork()
        val txWithAppliedRobber = futureWithRobberApplied.getOrThrow().coreTransaction

        val inputRobber = txWithAppliedRobber.outputsOfType<RobberState>().single()
        val outputRobber = txWithAppliedRobber.outputsOfType<RobberState>().single()

        requireThat {
            "The robber that was deactivated is the robber that was moved" using (outputRobber.linearId == inputRobber.linearId)
            "The robber has no changed position" using (outputRobber.hexTileIndex == inputRobber.hexTileIndex)
            "The output Robber has been deactivated" using (!outputRobber.active)
        }

    }

    @Test
    fun aPlayerIsAbleToRemoveAPlayBlockerState() {

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
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions)

        val gameBoardState = arrayOfAllTransactions.last().coreTransaction.outRefsOfType<GameBoardState>().first().state.data
        val nodeWithMoreThan7 = gatherResourcesUntilAPlayerHasMoreThan7(gameBoardState, arrayOfAllPlayerNodesInOrder, oracle, network)

        val diceRollTriggeringRobber = getDiceRollWithSpecifiedRollValue(3,4, gameBoardState, oracle)
        val futureWithRobberTriggered = arrayOfAllPlayerNodes[0].startFlow(RollDiceFlow(gameBoardState.linearId, diceRollTriggeringRobber))
        network.runNetwork()
        futureWithRobberTriggered.getOrThrow()

        val futureWithMovedRobber = arrayOfAllPlayerNodesInOrder[0].startFlow(MoveRobberFlow(gameBoardState.linearId, 5))
        network.runNetwork()
        futureWithMovedRobber.getOrThrow()

        val futureWithRobberApplied = arrayOfAllPlayerNodesInOrder[0].startFlow(ApplyRobberFlow(gameBoardState.linearId))
        network.runNetwork()
        val txWithAppliedRobber = futureWithRobberApplied.getOrThrow().coreTransaction

       val playBlockerState = txWithAppliedRobber.outputsOfType<PlayBlockerState>()
                .filter { it.playerBlocked == nodeWithMoreThan7.info.legalIdentities.first() }
                .first()

        var resourceTotal = 0
        val resourcesToSpend = mutableMapOf<TokenType, Long>()
        val playerResources = countAllResourcesForASpecificNode(nodeWithMoreThan7).mutableMap
        playerResources.forEach {
            if (resourceTotal < playBlockerState.price) {
                if (resourceTotal + it.value > playBlockerState.price) {
                    val amount = it.value + resourceTotal.toLong() - playBlockerState.price
                    resourcesToSpend[it.key] = amount
                    resourceTotal += amount.toInt()
                }
                else {
                    resourcesToSpend[it.key] = it.value
                    resourceTotal += it.value.toInt()
                }
            }
        }

        val futureWithRemovedPlayBlockerState = nodeWithMoreThan7.startFlow(RemovePlayBlockerFlow(playBlockerState.linearId, resourcesToSpend))
        network.runNetwork()
        futureWithRemovedPlayBlockerState.getOrThrow()

        requireThat {
            "All nodes now recognize that the nodeWithMoreThan7 has removed its playBlocker" using (
                    arrayOfAllPlayerNodes.all { it.services.vaultService.queryBy<PlayBlockerState>().states.filter { it.state.data.playerBlocked == nodeWithMoreThan7.info.legalIdentities.first() }.isEmpty() })
        }

    }

}