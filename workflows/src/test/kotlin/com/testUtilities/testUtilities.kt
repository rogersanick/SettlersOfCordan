package com.testUtilities

import com.contractsAndStates.states.*
import com.flows.BuildInitialSettlementAndRoadFlow
import com.flows.BuildSettlementFlow
import com.flows.EndTurnDuringInitialPlacementFlow
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import sun.tools.jstat.Token

fun placeAPieceFromASpecificNodeAndEndTurn(i: Int, testCoordinates: ArrayList<Pair<Int, Int>>, gameState: GameBoardState, network: MockNetwork, arrayOfAllPlayerNodesInOrder: List<StartedMockNode>, arrayOfAllTransactions: ArrayList<SignedTransaction>, initialSetupComplete: Boolean) {
    // Build an initial settlement by issuing a settlement state
    // and updating the current turn.
    if (gameState.hexTiles[testCoordinates[i].first].resourceType == "Desert") {
        testCoordinates[i] = Pair(testCoordinates[i].first + 7, testCoordinates[i].second)
    }

    val currPlayer = arrayOfAllPlayerNodesInOrder[i]

    if (initialSetupComplete) {
        // Build a settlement only
        val buildSettlementFlow = BuildSettlementFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second)
        val futureWithInitialSettlementBuild = currPlayer.startFlow(buildSettlementFlow)
        network.runNetwork()
        arrayOfAllTransactions.add(futureWithInitialSettlementBuild.getOrThrow())

        // End turn during normal game play
        currPlayer.startFlow(EndTurnDuringInitialPlacementFlow())
        network.runNetwork()
    } else {
        // Build an initial settlement and road
        val buildInitialSettlementFlow = BuildInitialSettlementAndRoadFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second, testCoordinates[i].second)
        val futureWithInitialSettlementBuild = currPlayer.startFlow(buildInitialSettlementFlow)
        network.runNetwork()
        arrayOfAllTransactions.add(futureWithInitialSettlementBuild.getOrThrow())

        // End turn during initial setup phase
        currPlayer.startFlow(EndTurnDuringInitialPlacementFlow())
        network.runNetwork()
    }

}

fun countAllResourcesForASpecificNode(node: StartedMockNode): MapOfResources {
    val mapOfResources = mutableMapOf<TokenType, Long>(
            Pair(Wheat, 0),
            Pair(Ore, 0),
            Pair(Wood, 0),
            Pair(Brick, 0),
            Pair(Sheep, 0)
    )

    node.services.vaultService.queryBy<FungibleToken<*>>().states.filter { it.state.data.holder.owningKey == node.info.legalIdentities.first().owningKey }.forEach {
        val tokenAmount = it.state.data.amount
        if (mapOfResources.containsKey<TokenType>(tokenAmount.token.tokenType)) mapOfResources[tokenAmount.token.tokenType] = mapOfResources[tokenAmount.token.tokenType]!!.plus(tokenAmount.quantity)
        else mapOfResources[tokenAmount.token.tokenType] = tokenAmount.quantity
    }

    return MapOfResources(mapOfResources)
}

class MapOfResources(val mutableMap: MutableMap<TokenType, Long>) {

    fun addTokenState(amount: Amount<TokenType>): MapOfResources {
        val mapToReturn = mutableMap.toMutableMap()
        mapToReturn[amount.token] = mapToReturn[amount.token]!!.plus(amount.quantity)
        return MapOfResources(mapToReturn)
    }

    fun subtractTokenState(amount: Amount<TokenType>): MapOfResources {
        val mapToReturn = mutableMap.toMutableMap()
        if (mapToReturn[amount.token]!!.minus(amount.quantity) < 0) throw IllegalArgumentException("A node can't have a negative amount of a resource type")
        else mapToReturn[amount.token] = mapToReturn[amount.token]!!.minus(amount.quantity)
        return MapOfResources(mapToReturn)
    }
}