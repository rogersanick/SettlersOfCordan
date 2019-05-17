package com.flowTests

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.Resource
import com.contractsAndStates.states.TradeState
import com.flows.*
import com.oracleService.flows.DiceRollRequestHandler
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.testUtilities.countAllResourcesForASpecificNode
import com.testUtilities.rollDiceThenGatherThenMaybeEndTurn
import com.testUtilities.setupGameBoardForTesting
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
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

class TradeWithPortFlowTest {
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
            it.registerInitiatedFlow(TradeWithPortFlowResponder::class.java)
        }

        listOf(DiceRollRequestHandler::class.java).forEach { oracle.registerInitiatedFlow(it) }

        network.runNetwork()
    }

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun player1IsAbleToMakeMultipleTradesWithAPortOnTheirTurn() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger
        fun getStxWithGameState(): SignedTransaction {
            val gameStateIssueFlow = (SetupGameBoardFlow(p1, p2, p3, p4))
            val futureWithGameState = a.startFlow(gameStateIssueFlow)
            network.runNetwork()
            return futureWithGameState.getOrThrow()
        }

        // Ensure that player 1 will be able to trade with the port with access from the HexTile at index 0.
        var stxGameState = getStxWithGameState()
        var gameBoardState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()
        while (
                gameBoardState.hexTiles[0].resourceType == "Desert" || !gameBoardState.ports[0].portTile.inputRequired.contains(Amount(2, Resource.getInstance(gameBoardState.hexTiles[0].resourceType)))
        ) {
            gameBoardState = getStxWithGameState().coreTransaction.outputsOfType<GameBoardState>().single()
        }

        // Get a reference to the issued game state
        val arrayOfAllTransactions = arrayListOf<SignedTransaction>()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameBoardState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameBoardState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions)

        // Roll the dice and collect resources for player 1
        val stxForFullTurn = rollDiceThenGatherThenMaybeEndTurn(gameBoardState.linearId, arrayOfAllPlayerNodesInOrder[0], network, false)

        requireThat {
            val resources = stxForFullTurn.stxWithIssuedResources.coreTransaction.outputsOfType<FungibleToken<*>>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val portToTradeWith = gameBoardState.ports[0]
        val inputResource: Resource = portToTradeWith.portTile.inputRequired.filter { it.token.symbol == Resource.getInstance(gameBoardState.hexTiles[0].resourceType).symbol }.single().token
        val outputResource: Resource = portToTradeWith.portTile.outputRequired.filter { it.token.symbol != inputResource.symbol }.first().token
        val player1ResourcesPreTrade = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])

        val futureWithIssuedTrade = arrayOfAllPlayerNodesInOrder[0].startFlow(
                TradeWithPortFlow(
                        gameBoardState.linearId,
                        0,
                        5,
                        inputResource.symbol,
                        outputResource.symbol,
                        1
                )
        )
        network.runNetwork()
        val txWithPortTrade = futureWithIssuedTrade.getOrThrow()
        val tradeToExecute = txWithPortTrade.coreTransaction.outputsOfType<TradeState>().single()

        val player1ResourcesPostTrade = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])

        assert(player1ResourcesPreTrade.addTokenState(Amount(1, outputResource)).subtractTokenState(Amount(2, inputResource)).mutableMap.all {
            player1ResourcesPostTrade.mutableMap[it.key] == it.value
        })

    }

}