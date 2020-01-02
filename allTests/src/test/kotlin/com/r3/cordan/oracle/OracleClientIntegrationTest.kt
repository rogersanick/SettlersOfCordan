package com.r3.cordan.oracle

import com.r3.cordan.oracle.client.states.DiceRollState
import com.r3.cordan.testutils.setupGameBoardForTesting
import com.r3.cordan.primary.flows.dice.RollDiceFlow
import com.r3.cordan.primary.flows.board.SetupGameBoardFlow
import com.r3.cordan.primary.states.structure.GameBoardState
import com.r3.cordan.testutils.BaseBoardGameTest
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentity
import org.junit.jupiter.api.Test

class OracleClientIntegrationTest: BaseBoardGameTest() {

    @Test
    fun oracleReturnsARandomDiceRoll() {
        val rollDiceFlow = RollDiceFlow(gameState.linearId)
        val futureWithDiceRoll = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        val txWithDiceRoll = futureWithDiceRoll.getOrThrow()
        val diceRollState = txWithDiceRoll.coreTransaction.outputsOfType<DiceRollState>().single()

        val diceRoll1 = diceRollState.randomRoll1
        val diceRoll2 = diceRollState.randomRoll2
        assert(diceRoll1 in 1..7)
        assert(diceRoll2 in 1..7)

    }

}
