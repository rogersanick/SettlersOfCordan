package com.r3.cordan.primary

import com.r3.cordan.primary.flows.structure.BuildCityFlow
import com.r3.cordan.primary.flows.structure.BuildSettlementFlow
import com.r3.cordan.primary.states.board.HexTileIndex
import com.r3.cordan.primary.states.resources.HexTileType
import com.r3.cordan.primary.states.structure.Buildable
import com.r3.cordan.primary.states.structure.SettlementState
import com.r3.cordan.primary.states.structure.getBuildableCosts
import com.r3.cordan.testutils.*
import net.corda.core.contracts.Amount
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class BuildFlowTests: BaseBoardGameTest() {

    @Test
    fun testAPlayerIsAbleToBuildASettlement() {
        gatherUntilThereAreEnoughResourcesForSpend(gameState, arrayOfAllPlayerNodesInOrder, oracle, network, getBuildableCosts(Buildable.Settlement))
        giveAllResourcesToPlayer1(gameState, arrayOfAllPlayerNodesInOrder, network)

        val resourcesPreSpend = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])
        val hexTileIndex = if (gameState.hexTiles[HexTileIndex(17)].resourceType == HexTileType.Desert) 15 else 17
        val stxWithSettlement = arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(BuildSettlementFlow(gameState.linearId, hexTileIndex, 2), network)
        var resourcesPostSpend = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])

        getBuildableCosts(Buildable.Settlement).forEach{
            resourcesPostSpend = resourcesPostSpend.addTokenState(Amount(it.value, it.key))
        }

        assertEquals(resourcesPreSpend.mutableMap, resourcesPostSpend.mutableMap, "The resources owned by player1 should reflect the spend required to build the development card.")
        assertEquals(stxWithSettlement.coreTransaction.outputsOfType<SettlementState>().first().owner,
                arrayOfAllPlayerNodesInOrder.first().info.legalIdentities.first(), "The settlement should be owned by player 1")
    }

    @Test
    fun testAPlayerIsAbleToBuildACity() {
        gatherUntilThereAreEnoughResourcesForSpend(gameState, arrayOfAllPlayerNodesInOrder, oracle, network, getBuildableCosts(Buildable.Settlement))
        giveAllResourcesToPlayer1(gameState, arrayOfAllPlayerNodesInOrder, network)
        val hexTileIndex = if (gameState.hexTiles[HexTileIndex(17)].resourceType == HexTileType.Desert) 15 else 17
        val stxWithSettlement = arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(BuildSettlementFlow(gameState.linearId, hexTileIndex, 2), network)
        val settlementState = stxWithSettlement.coreTransaction.outputsOfType<SettlementState>().single()

        gatherUntilThereAreEnoughResourcesForSpend(gameState, arrayOfAllPlayerNodesInOrder, oracle, network, getBuildableCosts(Buildable.City))
        giveAllResourcesToPlayer1(gameState, arrayOfAllPlayerNodesInOrder, network)
        val resourcesPreSpend = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])
        val stxWithCity = arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(BuildCityFlow(gameState.linearId, settlementState.linearId), network)
        var resourcesPostSpend = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])

        getBuildableCosts(Buildable.City).forEach{
            resourcesPostSpend = resourcesPostSpend.addTokenState(Amount(it.value, it.key))
        }

        assertEquals(resourcesPreSpend.mutableMap, resourcesPostSpend.mutableMap, "The resources owned by player1 should reflect the spend required to build the development card.")
        assertEquals(stxWithSettlement.coreTransaction.outputsOfType<SettlementState>().first().owner,
                arrayOfAllPlayerNodesInOrder.first().info.legalIdentities.first(), "The settlement should be owned by player 1")
    }

}