package com.r3.cordan.primary

import com.r3.cordan.primary.states.structure.Buildable
import com.r3.cordan.primary.states.structure.getBuildableCosts
import com.r3.cordan.testutils.BaseBoardGameTest
import com.r3.cordan.testutils.gatherUntilAPlayerHasEnoughForSpend
import org.junit.jupiter.api.Test

class DevelopmentCardFlowTest: BaseBoardGameTest() {

    @Test
    fun testAPlayerIsAbleToBuildADevelopmentCard() {
        gatherUntilAPlayerHasEnoughForSpend(gameState, arrayOfAllPlayerNodes, oracle, network, getBuildableCosts(Buildable.DevelopmentCard))
    }

}