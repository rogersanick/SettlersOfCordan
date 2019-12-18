package com.r3.cordan.primary

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.cordan.primary.flows.dice.RollDiceFlow
import com.r3.cordan.primary.flows.resources.GatherResourcesFlow
import com.r3.cordan.primary.flows.trade.ExecuteTradeFlow
import com.r3.cordan.primary.flows.trade.IssueTradeFlow
import com.r3.cordan.primary.flows.turn.EndTurnFlow
import com.r3.cordan.primary.states.trade.TradeState
import com.r3.cordan.testutils.*
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TradeFlowTests: BaseBoardGameTest() {

    @Test
    fun player1IsAbleToIssueATradeOnTheirTurn() {
        
        val rollDiceFlow = RollDiceFlow(gameState.linearId, getDiceRollWithRandomRollValue(gameState, oracle))
        val futureWithDiceRoll = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRoll.getOrThrow()

        val futureWithClaimedResources = arrayOfAllPlayerNodesInOrder[0].startFlow(GatherResourcesFlow(gameBoardLinearId = gameState.linearId))
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
                        gameState.linearId
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

        val rollDiceFlow = RollDiceFlow(gameState.linearId, getDiceRollWithRandomRollValue(gameState, oracle))
        val futureWithDiceRoll = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRoll.getOrThrow()

        val futureWithClaimedResources = arrayOfAllPlayerNodesInOrder[0].startFlow(GatherResourcesFlow(gameBoardLinearId = gameState.linearId))
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
                        gameState.linearId
                )
        )
        network.runNetwork()
        val txWithIssuedTrade = futureWithIssuedTrade.getOrThrow()
        val tradeToExecute = txWithIssuedTrade.coreTransaction.outputsOfType<TradeState>().single()
        val linearIdOfTradeToExecute = tradeToExecute.linearId

        val futureWithPlayer1TurnEnded = arrayOfAllPlayerNodesInOrder[0].startFlow(EndTurnFlow(gameState.linearId))
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
