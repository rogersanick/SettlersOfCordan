package com.flowTests

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.TradeState
import com.flows.*
import com.oracleService.flows.DiceRollRequestHandler
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.testUtilities.placeAPieceFromASpecificNodeAndEndTurn
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
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

class TradeFlowTests {
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
            it.registerInitiatedFlow(SetupGameBoardFlowResponder::class.java)
            it.registerInitiatedFlow(BuildInitialSettlementFlowResponder::class.java)
            it.registerInitiatedFlow(IssueResourcesFlowResponder::class.java)
            it.registerInitiatedFlow(ExecuteTradeFlowResponder::class.java)
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
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayListOf(Pair(0,5), Pair(0,3), Pair(1,5), Pair(1,2))
        val nonconflictingHextileIndexAndCoordinatesRound2 = arrayListOf(Pair(10,5), Pair(10,3), Pair(11,5), Pair(11,2))

        for (i in 0..3) {
            placeAPieceFromASpecificNodeAndEndTurn(i, nonconflictingHextileIndexAndCoordinatesRound1, gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions, false)
        }

        for (i in 3.downTo(0)) {
            placeAPieceFromASpecificNodeAndEndTurn(i, nonconflictingHextileIndexAndCoordinatesRound2, gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions, false)
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

        val player1Resources = arrayOfAllPlayerNodesInOrder[0].services.vaultService.queryBy<FungibleToken<*>>().states.firstOrNull()?.state?.data?.amount
        val player2Resources = arrayOfAllPlayerNodesInOrder[1].services.vaultService.queryBy<FungibleToken<*>>().states.firstOrNull()?.state?.data?.amount

        val futureWithIssuedTrade = arrayOfAllPlayerNodesInOrder[0].startFlow(
                IssueTradeFlow(
                    Amount(player1Resources!!.quantity, player1Resources.token.tokenType),
                    Amount(player2Resources!!.quantity, player2Resources.token.tokenType),
                    arrayOfAllPlayerNodesInOrder[1].info.legalIdentities.single(),
                    gameBoardState.linearId
                )
        )
        network.runNetwork()
        val txWithIssuedTrade = futureWithIssuedTrade.getOrThrow()
        val linearIdOfTradeToExecute = txWithIssuedTrade.coreTransaction.outputsOfType<TradeState>().single().linearId

        val futureWithPlayer1TurnEnded = arrayOfAllPlayerNodesInOrder[0].startFlow(EndTurnFlow())
        network.runNetwork()
        futureWithPlayer1TurnEnded.getOrThrow()

        val futureWithExecutedTrade = arrayOfAllPlayerNodesInOrder[1].startFlow(ExecuteTradeFlow(linearIdOfTradeToExecute))
        network.runNetwork()
        futureWithExecutedTrade.getOrThrow()

        val newPlayer1Resources = arrayOfAllPlayerNodesInOrder[0].services.vaultService.queryBy<FungibleToken<*>>().states
        val newPlayer2Resources = arrayOfAllPlayerNodesInOrder[1].services.vaultService.queryBy<FungibleToken<*>>().states
    }

}
