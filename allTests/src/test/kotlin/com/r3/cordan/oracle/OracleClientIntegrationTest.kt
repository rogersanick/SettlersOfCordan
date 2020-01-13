package com.r3.cordan.oracle

import com.r3.cordan.oracle.client.states.DiceRollState
import com.r3.cordan.primary.flows.random.RollDiceFlow
import com.r3.cordan.testutils.BaseBoardGameTest
import com.r3.cordan.testutils.runFlowAndReturn
import net.corda.core.utilities.getOrThrow
import org.junit.jupiter.api.Test

class OracleClientIntegrationTest: BaseBoardGameTest() {

    @Test
    fun oracleReturnsARandomDiceRoll() {
        val rollDiceFlow = RollDiceFlow(gameState.linearId)
        val txWithDiceRoll = arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(rollDiceFlow, network)
        val diceRollState = txWithDiceRoll.coreTransaction.outputsOfType<DiceRollState>().single()

        val diceRoll1 = diceRollState.randomRoll1
        val diceRoll2 = diceRollState.randomRoll2
        assert(diceRoll1 in 1..7)
        assert(diceRoll2 in 1..7)

    }

}
