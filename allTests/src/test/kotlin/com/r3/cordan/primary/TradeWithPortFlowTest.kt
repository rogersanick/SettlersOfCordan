package com.r3.cordan.primary

import com.r3.cordan.primary.flows.board.SetupGameBoardFlow
import com.r3.cordan.primary.flows.trade.TradeWithPortFlow
import com.r3.cordan.primary.states.structure.GameBoardState
import com.r3.cordan.primary.states.board.HexTileIndex
import com.r3.cordan.primary.states.resources.HexTileType
import com.r3.cordan.primary.states.resources.Resource
import com.r3.cordan.testutils.*
import net.corda.core.contracts.Amount
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentity
import org.junit.jupiter.api.Test

class TradeWithPortFlowTest: BaseBoardGameTest() {

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
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameBoardState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameBoardState, network, arrayOfAllPlayerNodesInOrder)

        val portToTradeWith = gameBoardState.ports.value[0]
        val outputResource = portToTradeWith.portTile.outputRequired.first().token as Resource
        val inputResource = portToTradeWith.portTile.getInputOf(
                gameBoardState.hexTiles.get(HexTileIndex(0)).resourceType.resourceYielded!!).token as Resource

        val rollTrigger = gameBoardState.hexTiles.get(HexTileIndex(0)).rollTrigger!!.total
        val diceRollPips1 = rollTrigger / 2
        val diceRollPips2 = (rollTrigger / 2) + (rollTrigger % 2)

        for (i in 0..3) {
            val diceRoll = getDiceRollWithSpecifiedRollValue(diceRollPips1, diceRollPips2, gameBoardState, oracle)
            rollDiceThenGatherThenMaybeEndTurn(gameBoardState.linearId, arrayOfAllPlayerNodesInOrder[i], network, true, diceRoll)
        }

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
        futureWithIssuedTrade.getOrThrow()

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