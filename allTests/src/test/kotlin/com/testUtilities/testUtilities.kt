package com.testUtilities

import com.contractsAndStates.states.*
import com.flows.*
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode

fun setupGameBoardForTesting(gameState: GameBoardState, network: MockNetwork, arrayOfAllPlayerNodesInOrder: List<StartedMockNode>, arrayOfAllTransactions: ArrayList<SignedTransaction>) {
    val nonconflictingHextileIndexAndCoordinatesRound1 = arrayListOf(Pair(0, 5), Pair(1, 3), Pair(2, 5), Pair(2, 2))
    val nonconflictingHextileIndexAndCoordinatesRound2 = arrayListOf(Pair(10, 5), Pair(10, 3), Pair(11, 5), Pair(11, 2))

    for (i in 0..3) {
        placeAPieceFromASpecificNodeAndEndTurn(i, nonconflictingHextileIndexAndCoordinatesRound1, gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions, false)
    }

    for (i in 3.downTo(0)) {
        placeAPieceFromASpecificNodeAndEndTurn(i, nonconflictingHextileIndexAndCoordinatesRound2, gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions, false)
    }
}

fun placeAPieceFromASpecificNodeAndEndTurn(i: Int, testCoordinates: ArrayList<Pair<Int, Int>>, gameState: GameBoardState, network: MockNetwork, arrayOfAllPlayerNodesInOrder: List<StartedMockNode>, arrayOfAllTransactions: ArrayList<SignedTransaction>, initialSetupComplete: Boolean) {
    // Build an initial settlement by issuing a settlement state
    // and updating the current turn.
    if (gameState.hexTiles.get(HexTileIndex(testCoordinates[i].first)).resourceType == HexTileType.Desert) {
        testCoordinates[i] = Pair(testCoordinates[i].first + 6, testCoordinates[i].second)
    }

    val currPlayer = arrayOfAllPlayerNodesInOrder[i]

    if (initialSetupComplete) {
        // Build a settlement only
        val buildSettlementFlow = BuildSettlementFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second)
        val futureWithInitialSettlementBuild = currPlayer.startFlow(buildSettlementFlow)
        network.runNetwork()
        arrayOfAllTransactions.add(futureWithInitialSettlementBuild.getOrThrow())

        // End turn during normal game play
        currPlayer.startFlow(EndTurnDuringInitialPlacementFlow(gameState.linearId))
        network.runNetwork()
    } else {
        // Build an initial settlement and road
        val buildInitialSettlementFlow = BuildInitialSettlementAndRoadFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second, testCoordinates[i].second)
        val futureWithInitialSettlementBuild = currPlayer.startFlow(buildInitialSettlementFlow)
        network.runNetwork()
        arrayOfAllTransactions.add(futureWithInitialSettlementBuild.getOrThrow())

        // End turn during initial setup phase
        currPlayer.startFlow(EndTurnDuringInitialPlacementFlow(gameState.linearId))
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

    node.services.vaultService.queryBy<FungibleToken>().states.filter { it.state.data.holder.owningKey == node.info.legalIdentities.first().owningKey }.forEach {
        val tokenAmount = it.state.data.amount
        if (mapOfResources.containsKey<TokenType>(tokenAmount.token.tokenType)) mapOfResources[tokenAmount.token.tokenType] = mapOfResources[tokenAmount.token.tokenType]!!.plus(tokenAmount.quantity)
        else mapOfResources[tokenAmount.token.tokenType] = tokenAmount.quantity
    }

    return MapOfResources(mapOfResources)
}

fun rollDiceThenGatherThenMaybeEndTurn(gameBoardLinearId: UniqueIdentifier, node: StartedMockNode, network: MockNetwork, endTurn: Boolean = true): ResourceCollectionSignedTransactions {

    // Roll the dice
    val futureWithDiceRoll = node.startFlow(RollDiceFlow(gameBoardLinearId))
    network.runNetwork()
    val stxWithDiceRoll = futureWithDiceRoll.getOrThrow()

    // Collect Resources
    val futureWithResources = node.startFlow(GatherResourcesFlow(gameBoardLinearId))
    network.runNetwork()
    val stxWithIssuedResources = futureWithResources.getOrThrow()

    // End Turn if applicable
    var stxWithEndedTurn: SignedTransaction? = null
    if (endTurn) {
        val futureWithEndedTurn = node.startFlow(EndTurnFlow(gameBoardLinearId))
        network.runNetwork()
        stxWithEndedTurn = futureWithEndedTurn.getOrThrow()
    }

    return ResourceCollectionSignedTransactions(stxWithDiceRoll, stxWithIssuedResources, stxWithEndedTurn)
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