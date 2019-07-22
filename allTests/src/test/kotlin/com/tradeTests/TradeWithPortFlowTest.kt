package com.tradeTests

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.HexTileIndex
import com.contractsAndStates.states.HexTileType
import com.contractsAndStates.states.Resource
import com.flows.SetupGameBoardFlow
import com.flows.TradeWithPortFlow
import com.testUtilities.countAllResourcesForASpecificNode
import com.testUtilities.rollDiceThenGatherThenMaybeEndTurn
import com.testUtilities.setupGameBoardForTesting
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class TradeWithPortFlowTest {
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

    /**
     * TODO: This flow is particularly difficult to test because after the initial setup,
     * no party will have sufficient resources to trade with a port. All resource generation after the fact is random.
     *
     * Solution to this would be refactoring to generate specific test scenarios without the need for convulated flows
     * accounting for randomness.
     */

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
        val stxGameState = getStxWithGameState()
        var gameBoardState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()
        while (
        // TODO make sure the it.key.resourceYielded is not null
                gameBoardState.hexTiles.get(HexTileIndex(0)).resourceType == HexTileType.Desert || !gameBoardState.ports.value[0].portTile.inputRequired.contains(Amount(2, gameBoardState.hexTiles.get(HexTileIndex(0)).resourceType.resourceYielded!!))
        ) {
            gameBoardState = getStxWithGameState().coreTransaction.outputsOfType<GameBoardState>().single()
        }

        // Get a reference to the issued game state
        val arrayOfAllTransactions = arrayListOf<SignedTransaction>()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameBoardState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameBoardState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions)

        var currPlayer = 0
        while (!(countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0]).mutableMap.any { it.value > 1 }) || currPlayer != 0) {
            rollDiceThenGatherThenMaybeEndTurn(gameBoardState.linearId, arrayOfAllPlayerNodesInOrder[currPlayer], network, true)
            currPlayer = if (currPlayer == 3) 0 else currPlayer + 1
        }

        val portToTradeWith = gameBoardState.ports.value[0]
        val inputResource = portToTradeWith.portTile.getInputOf(
                gameBoardState.hexTiles.get(HexTileIndex(0)).resourceType.resourceYielded!!).token as Resource
        val outputResource = portToTradeWith.portTile.getOutputOf(inputResource).token as Resource
        val playerWithPortPreTrade = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])

        val futureWithIssuedTrade = arrayOfAllPlayerNodesInOrder[0].startFlow(
                TradeWithPortFlow(
                        gameBoardState.linearId,
                        0,
                        5,
                        inputResource,
                        outputResource
                )
        )
        network.runNetwork()
        val txWithExecutedPortTrade = futureWithIssuedTrade.getOrThrow()

        val playerWithPortPostTrade = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])

        assert(playerWithPortPreTrade.addTokenState(Amount(1, outputResource)).subtractTokenState(Amount(2, inputResource)).mutableMap.all {
            playerWithPortPostTrade.mutableMap[it.key] == it.value
        })

    }

}