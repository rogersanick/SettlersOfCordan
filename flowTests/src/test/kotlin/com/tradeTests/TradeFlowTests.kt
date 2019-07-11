package com.tradeTests

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.TradeState
import com.flows.*
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.testUtilities.countAllResourcesForASpecificNode
import com.testUtilities.setupGameBoardForTesting
import net.corda.core.contracts.Amount
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

class TradeFlowTests {
    private val network = MockNetwork(MockNetworkParameters(
            notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary", "London", "GB"))),
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
            cordappsForAllNodes = listOf(
                    TestCordapp.findCordapp("com.oracleService"),
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
    fun player1IsAbleToIssueATradeOnTheirTurn() {

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

        val player1Resources = arrayOfAllPlayerNodesInOrder[0].services.vaultService.queryBy<FungibleToken>().states.firstOrNull()?.state?.data?.amount
        val player2Resources = arrayOfAllPlayerNodesInOrder[1].services.vaultService.queryBy<FungibleToken>().states.firstOrNull()?.state?.data?.amount

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
        val issuedTrades = txWithIssuedTrade.coreTransaction.outputsOfType<TradeState>()

        requireThat {
            "A trade must have been issued." using (issuedTrades.size == 1)
        }

    }

    @Test
    fun player2IsAbleToExecuteATradeImposedByPlayer1() {

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

        val player1ResourcesPreTrade = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])
        val player2ResourcesPreTrade = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[1])

        val player1ResourceToTrade = arrayOfAllPlayerNodesInOrder[0].services.vaultService.queryBy<FungibleToken>().states.filter { it.state.data.holder.owningKey == arrayOfAllPlayerNodesInOrder[0].info.legalIdentities.first().owningKey }.firstOrNull()?.state?.data?.amount
        val player2ResourceToTrade = arrayOfAllPlayerNodesInOrder[1].services.vaultService.queryBy<FungibleToken>().states.filter { it.state.data.holder.owningKey == arrayOfAllPlayerNodesInOrder[1].info.legalIdentities.first().owningKey && it.state.data.amount.token.tokenType != player1ResourceToTrade!!.token.tokenType }.firstOrNull()?.state?.data?.amount

        val futureWithIssuedTrade = arrayOfAllPlayerNodesInOrder[0].startFlow(
                IssueTradeFlow(
                    Amount(player1ResourceToTrade!!.quantity, player1ResourceToTrade.token.tokenType),
                    Amount(player2ResourceToTrade!!.quantity, player2ResourceToTrade.token.tokenType),
                    arrayOfAllPlayerNodesInOrder[1].info.legalIdentities.single(),
                    gameBoardState.linearId
                )
        )
        network.runNetwork()
        val txWithIssuedTrade = futureWithIssuedTrade.getOrThrow()
        val tradeToExecute = txWithIssuedTrade.coreTransaction.outputsOfType<TradeState>().single()
        val linearIdOfTradeToExecute = tradeToExecute.linearId

        val futureWithPlayer1TurnEnded = arrayOfAllPlayerNodesInOrder[0].startFlow(EndTurnFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer1TurnEnded.getOrThrow()

        val futureWithExecutedTrade = arrayOfAllPlayerNodesInOrder[1].startFlow(ExecuteTradeFlow(linearIdOfTradeToExecute))
        network.runNetwork()
        futureWithExecutedTrade.getOrThrow()

        val player1ResourcesPostTrade = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])
        val player2ResourcesPostTrade = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[1])

        assert(player1ResourcesPreTrade.addTokenState(tradeToExecute.wanted).subtractTokenState(tradeToExecute.offering).mutableMap.all {
            player1ResourcesPostTrade.mutableMap[it.key] == it.value
        })

        assert(player2ResourcesPreTrade.addTokenState(tradeToExecute.offering).subtractTokenState(tradeToExecute.wanted).mutableMap.all {
            player2ResourcesPostTrade.mutableMap[it.key] == it.value
        })

    }

}
