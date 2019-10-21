package com.testUtilities

import com.contractsAndStates.states.*
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.node.StartedMockNode

fun countAllResourcesForASpecificNode(node: StartedMockNode): MapOfResources {
    val mapOfResources = mutableMapOf<TokenType, Long>(
            Pair(Wheat, 0),
            Pair(Ore, 0),
            Pair(Wood, 0),
            Pair(Brick, 0),
            Pair(Sheep, 0)
    )

    node.services.vaultService.queryBy<GameCurrencyState>().states.filter { it.state.data.holder.owningKey == node.info.legalIdentities.first().owningKey }.forEach {
        val tokenAmount = it.state.data.amount
        if (mapOfResources.containsKey<TokenType>(tokenAmount.token.tokenType)) mapOfResources[tokenAmount.token.tokenType] = mapOfResources[tokenAmount.token.tokenType]!!.plus(tokenAmount.quantity)
        else mapOfResources[tokenAmount.token.tokenType] = tokenAmount.quantity
    }

    return MapOfResources(mapOfResources)
}

fun countAllKnownResourcesForASpecificNodeFromAllNodes(dataNodes: Collection<StartedMockNode>, targetNode: StartedMockNode): Collection<MutableMap<TokenType, Long>> {
    return dataNodes.map { countAllKnownResourcesForASpecificNode(it, targetNode).mutableMap }
}

fun countAllKnownResourcesForASpecificNode(dataNode: StartedMockNode, targetNode: StartedMockNode): MapOfResources {
    val mapOfResources = mutableMapOf<TokenType, Long>(
            Pair(Wheat, 0),
            Pair(Ore, 0),
            Pair(Wood, 0),
            Pair(Brick, 0),
            Pair(Sheep, 0)
    )

    dataNode.services.vaultService.queryBy<GameCurrencyState>().states.filter { it.state.data.holder.owningKey == targetNode.info.legalIdentities.first().owningKey }.forEach {
        val tokenAmount = it.state.data.amount
        if (mapOfResources.containsKey<TokenType>(tokenAmount.token.tokenType)) mapOfResources[tokenAmount.token.tokenType] = mapOfResources[tokenAmount.token.tokenType]!!.plus(tokenAmount.quantity)
        else mapOfResources[tokenAmount.token.tokenType] = tokenAmount.quantity
    }

    return MapOfResources(mapOfResources)
}

class ResourceCollectionSignedTransactions(val stxWithDiceRoll: SignedTransaction, val stxWithIssuedResources: SignedTransaction, val stxWithEndedTurn: SignedTransaction?)

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

