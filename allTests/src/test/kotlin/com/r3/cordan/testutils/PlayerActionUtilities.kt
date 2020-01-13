package com.r3.cordan.testutils

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.cordan.oracle.client.states.DiceRollState
import com.r3.cordan.primary.flows.structure.BuildInitialSettlementAndRoadFlow
import com.r3.cordan.primary.flows.structure.BuildSettlementFlow
import com.r3.cordan.primary.flows.random.RollDiceFlow
import com.r3.cordan.primary.flows.resources.GatherResourcesFlow
import com.r3.cordan.primary.flows.trade.ExecuteTradeFlow
import com.r3.cordan.primary.flows.trade.IssueTradeFlow
import com.r3.cordan.primary.flows.turn.EndTurnDuringInitialPlacementFlow
import com.r3.cordan.primary.flows.turn.EndTurnFlow
import com.r3.cordan.primary.states.structure.GameBoardState
import com.r3.cordan.primary.states.resources.GameCurrencyState
import com.r3.cordan.primary.states.board.HexTileIndex
import com.r3.cordan.primary.states.resources.HexTileType
import com.r3.cordan.primary.states.resources.Wheat
import com.r3.cordan.primary.states.trade.TradeState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.internal.sumByLong
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode

fun rollDiceThenGatherThenMaybeEndTurn(
        gameBoardLinearId: UniqueIdentifier,
        node: StartedMockNode,
        network: MockNetwork,
        endTurn: Boolean = true,
        diceRollState: DiceRollState? = null): ResourceCollectionSignedTransactions {

    // Roll the random
    val stxWithDiceRoll: SignedTransaction = when (diceRollState) {
        null -> node.runFlowAndReturn(RollDiceFlow(gameBoardLinearId), network)
        else -> node.runFlowAndReturn(RollDiceFlow(gameBoardLinearId, diceRollState), network)
    }

    // Collect Resources
    val stxWithIssuedResources = node.runFlowAndReturn(GatherResourcesFlow(gameBoardLinearId), network)

    // End Turn if applicable
    var stxWithEndedTurn: SignedTransaction? = null
    if (endTurn) {
        stxWithEndedTurn = node.runFlowAndReturn(EndTurnFlow(gameBoardLinearId), network)
    }

    return ResourceCollectionSignedTransactions(stxWithDiceRoll, stxWithIssuedResources, stxWithEndedTurn)
}

fun placeAPieceFromASpecificNodeAndEndTurn(
        i: Int,
        testCoordinates: ArrayList<Pair<Int, Int>>,
        gameState: GameBoardState,
        network: MockNetwork,
        arrayOfAllPlayerNodesInOrder: List<StartedMockNode>,
        initialSetupComplete: Boolean): List<SignedTransaction> {
    // Build an initial settlement by issuing a settlement state
    // and updating the current turn.
    if (gameState.hexTiles.get(HexTileIndex(testCoordinates[i].first)).resourceType == HexTileType.Desert) {
        if (testCoordinates[i].first > 9) {
            testCoordinates[i] = Pair(testCoordinates[i].first + 1, testCoordinates[i].second)
        } else {
            testCoordinates[i] = Pair(testCoordinates[i].first + 3, testCoordinates[i].second)
        }
    }

    val currPlayer = arrayOfAllPlayerNodesInOrder[i]

    if (initialSetupComplete) {
        // Build a settlement only
        val buildSettlementFlow = BuildSettlementFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second)
        val txWithUpdatedGameBoardState = currPlayer.runFlowAndReturn(buildSettlementFlow, network)

        // End turn during normal game play
        val txWithUpdatedTurnState = currPlayer.runFlowAndReturn(buildSettlementFlow, network)

        return listOf(txWithUpdatedGameBoardState, txWithUpdatedTurnState)
    } else {
        // Build an initial settlement and road
        val buildInitialSettlementFlow = BuildInitialSettlementAndRoadFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second, testCoordinates[i].second)
        val txWithUpdatedGameBoardState = currPlayer.runFlowAndReturn(buildInitialSettlementFlow, network)

        // End turn during initial setup phase
        val txWithUpdatedTurnState = currPlayer.runFlowAndReturn(EndTurnDuringInitialPlacementFlow(gameState.linearId), network)

        return listOf(txWithUpdatedGameBoardState, txWithUpdatedTurnState)
    }

}

fun gatherUntilAPlayerHasMoreThan7(gameBoardState: GameBoardState,
                                   listOfNodes: List<StartedMockNode>,
                                   oracle: StartedMockNode,
                                   network: MockNetwork): StartedMockNode {
    var nodeWithMoreThan7Resources: StartedMockNode? = null
    while(nodeWithMoreThan7Resources == null) {
        for (nodeIndex in 0..3) {
            val diceRoll = getDiceRollWithRandomRollValue(gameBoardState, oracle)
            rollDiceThenGatherThenMaybeEndTurn(gameBoardState.linearId, listOfNodes[nodeIndex], network, diceRollState = diceRoll)
        }

        val nodeResources = listOfNodes
                .map { it to it.services.vaultService.queryBy<GameCurrencyState>().states
                        .filter { token -> token.state.data.fungibleToken.holder.owningKey == it.info.legalIdentities.first().owningKey }}

        nodeResources.forEach { node ->
            if (node.second.sumByLong { it.state.data.fungibleToken.amount.quantity } > 7) {
                nodeWithMoreThan7Resources = node.first
            }
        }
    }

    return nodeWithMoreThan7Resources!!
}

fun gatherUntilThereAreEnoughResourcesForSpend(gameBoardState: GameBoardState,
                                        listOfNodes: List<StartedMockNode>,
                                        oracle: StartedMockNode,
                                        network: MockNetwork,
                                        resourceCost: Map<TokenType, Long>) {
    var nodesDoNotHaveEnoughResources: Boolean = true
    while(nodesDoNotHaveEnoughResources) {
        for (nodeIndex in 0..3) {
            val diceRoll = getDiceRollWithRandomRollValue(gameBoardState, oracle)
            rollDiceThenGatherThenMaybeEndTurn(gameBoardState.linearId, listOfNodes[nodeIndex], network, diceRollState = diceRoll)
        }

        val nodeResources = listOfNodes
                .map { countAllResourcesForASpecificNode(it) }

        val allNodeResources = nodeResources.fold(MapOfResources(mutableMapOf())) { acc, map ->
            map.mutableMap.forEach { if (acc.mutableMap[it.key] != null) acc.mutableMap[it.key] = acc.mutableMap[it.key]!!.plus(it.value) else acc.mutableMap[it.key] = it.value }
            acc
        }

        if (allNodeResources.mutableMap.all { (t, a) -> a > resourceCost[t] ?: 0 }) {
            nodesDoNotHaveEnoughResources = false
        }
    }
}

fun giveAllResourcesToPlayer1(gameBoardState: GameBoardState,
                              listOfNodes: List<StartedMockNode>,
                              network: MockNetwork) {
    val player1 = listOfNodes[0]
    val otherPlayers = listOfNodes - player1
    otherPlayers.forEach { node ->
        countAllResourcesForASpecificNode(node).mutableMap.forEach {
            if (it.value > 0) {
                val tradeState = player1.runFlowAndReturn(
                        IssueTradeFlow(Amount.zero(Wheat), Amount(it.value, it.key), node.info.legalIdentities.first(), gameBoardState.linearId),
                        network
                ).coreTransaction.outputsOfType<TradeState>().single()
                node.runFlowAndReturn(ExecuteTradeFlow(tradeState.linearId), network)
            }
        }
    }
}