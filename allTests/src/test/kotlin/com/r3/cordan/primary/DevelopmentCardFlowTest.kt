package com.r3.cordan.primary

import com.r3.cordan.primary.flows.development.BuildDevelopmentCardFlow
import com.r3.cordan.primary.flows.development.RevealDevelopmentCardFlow
import com.r3.cordan.primary.states.development.RevealedDevelopmentCardState
import com.r3.cordan.primary.states.development.FaceDownDevelopmentCardState
import com.r3.cordan.primary.states.resources.Wheat
import com.r3.cordan.primary.states.structure.Buildable
import com.r3.cordan.primary.states.structure.getBuildableCosts
import com.r3.cordan.testutils.*
import net.corda.core.contracts.Amount
import net.corda.finance.DOLLARS
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DevelopmentCardFlowTest: BaseBoardGameTest() {

    @Test
    fun testAPlayerIsAbleToBuildADevelopmentCard() {
        gatherUntilThereAreEnoughResourcesForSpend(gameState, arrayOfAllPlayerNodesInOrder, oracle, network, getBuildableCosts(Buildable.DevelopmentCard))
        giveAllResourcesToPlayer1(gameState, arrayOfAllPlayerNodesInOrder, network)

        val resourcesPreSpend = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])
        val stxWithFaceDownDevelopmentCard = arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(BuildDevelopmentCardFlow(gameState.linearId), network)
        var resourcesPostSpend = countAllResourcesForASpecificNode(arrayOfAllPlayerNodesInOrder[0])

        getBuildableCosts(Buildable.DevelopmentCard).forEach{
            resourcesPostSpend = resourcesPostSpend.addTokenState(Amount(it.value, it.key))
        }

        assertEquals(resourcesPreSpend.mutableMap, resourcesPostSpend.mutableMap, "The resources owned by player1 should reflect the spend required to build the development card.")

        val developmentCard = stxWithFaceDownDevelopmentCard.coreTransaction.outputsOfType<FaceDownDevelopmentCardState>().single()
        val stxWithRevealedDevelopmentCard = arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(RevealDevelopmentCardFlow(gameState.linearId, developmentCard.linearId), network)
    }

}