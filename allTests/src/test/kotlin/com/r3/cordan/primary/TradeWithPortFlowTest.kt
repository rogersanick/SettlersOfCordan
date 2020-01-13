package com.r3.cordan.primary

import com.r3.cordan.primary.flows.trade.TradeWithPortFlow
import com.r3.cordan.primary.states.resources.Resource
import com.r3.cordan.testutils.*
import net.corda.core.contracts.Amount
import org.junit.jupiter.api.Test

class TradeWithPortFlowTest: BaseBoardGameTest() {

    @Test
    fun player1IsAbleToMakeMultipleTradesWithAPortOnTheirTurn() {

        val portToTradedWith = gameState.ports.value[0]
        gatherUntilThereAreEnoughResourcesForSpend(
                gameState,
                arrayOfAllPlayerNodesInOrder,
                oracle,
                network,
                portToTradedWith.portTile.inputRequired.map { it.token to it.quantity }.toMap())
        giveAllResourcesToPlayer1(gameState, arrayOfAllPlayerNodesInOrder, network)


        val outputResource = portToTradedWith.portTile.outputRequired.first().token as Resource
        val inputResource = portToTradedWith.portTile.inputRequired.first().token as Resource

        val playerWithPortPreTrade = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])
        arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(
                TradeWithPortFlow(
                        gameState.linearId,
                        0,
                        5,
                        inputResource,
                        outputResource
                ),
                network
        )

        val playerWithPortPostTrade = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])

        assert(playerWithPortPreTrade
                .addTokenState(Amount(1, outputResource))
                .subtractTokenState(Amount(2, inputResource))
                .mutableMap
                .all {
                    playerWithPortPostTrade.mutableMap[it.key] == it.value
                })

    }

}