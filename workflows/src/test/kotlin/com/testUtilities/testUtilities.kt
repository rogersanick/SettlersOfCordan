package com.testUtilities

import com.contractsAndStates.states.GameBoardState
import com.flows.BuildInitialSettlementFlow
import com.flows.EndTurnDuringInitialPlacementFlow
import com.flows.EndTurnFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode

fun placeAPieceFromASpecificNode(i: Int, testCoordinates: ArrayList<Pair<Int, Int>>, gameState: GameBoardState, network: MockNetwork, arrayOfAllPlayerNodesInOrder: List<StartedMockNode>, arrayOfAllTransactions: ArrayList<SignedTransaction>, initialSetupComplete: Boolean) {
    // Build an initial settlement by issuing a settlement state
    // and updating the current turn.
    if (gameState.hexTiles[testCoordinates[i].first].resourceType == "Desert") {
        testCoordinates[i] = Pair(testCoordinates[i].first + 7, testCoordinates[i].second)
    }

    val currPlayer = arrayOfAllPlayerNodesInOrder[i]

    val buildInitialSettlementFlow = BuildInitialSettlementFlow(gameState.linearId, testCoordinates[i].first, testCoordinates[i].second)
    val futureWithInitialSettlementBuild = currPlayer.startFlow(buildInitialSettlementFlow)
    network.runNetwork()
    arrayOfAllTransactions.add(futureWithInitialSettlementBuild.getOrThrow())

    currPlayer.startFlow(if (initialSetupComplete) EndTurnFlow() else EndTurnDuringInitialPlacementFlow())
    network.runNetwork()
}