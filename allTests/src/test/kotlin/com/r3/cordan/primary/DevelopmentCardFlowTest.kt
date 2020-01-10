package com.r3.cordan.primary

import com.r3.cordan.primary.flows.development.BuildDevelopmentCardFlow
import com.r3.cordan.primary.states.development.DevelopmentCardState
import com.r3.cordan.primary.states.structure.Buildable
import com.r3.cordan.primary.states.structure.getBuildableCosts
import com.r3.cordan.testutils.BaseBoardGameTest
import com.r3.cordan.testutils.gatherUntilThereAreEnoughResourcesForSpend
import com.r3.cordan.testutils.giveAllResourcesToPlayer1
import com.r3.cordan.testutils.runFlowAndReturn
import org.junit.jupiter.api.Test

class DevelopmentCardFlowTests: BaseBoardGameTest() {

    @Test
    fun testAPlayerIsAbleToBuildADevelopmentCard() {
        gatherUntilThereAreEnoughResourcesForSpend(gameState, arrayOfAllPlayerNodesInOrder, oracle, network, getBuildableCosts(Buildable.DevelopmentCard))
        giveAllResourcesToPlayer1(gameState, arrayOfAllPlayerNodesInOrder, network)
        val stxWithDevelopmentCard = arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(BuildDevelopmentCardFlow(gameState.linearId), network)
        val developmentCardIssued = stxWithDevelopmentCard.coreTransaction.outputsOfType<DevelopmentCardState>().first()
    }

}