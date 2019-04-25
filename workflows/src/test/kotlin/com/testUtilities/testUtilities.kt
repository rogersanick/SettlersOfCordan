package com.testUtilities

import com.contractsAndStates.states.GameBoardState
import com.flows.BuildInitialSettlementAndRoadFlow
import com.flows.BuildSettlementFlow
import com.flows.EndTurnDuringInitialPlacementFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode

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