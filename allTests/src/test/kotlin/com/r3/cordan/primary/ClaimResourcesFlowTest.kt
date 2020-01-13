package com.r3.cordan.primary

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.cordan.primary.flows.turn.EndTurnFlow
import com.r3.cordan.primary.flows.resources.GatherResourcesFlow
import com.r3.cordan.primary.flows.random.RollDiceFlow
import com.r3.cordan.testutils.*
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowException
import net.corda.core.utilities.getOrThrow
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ClaimResourcesFlowTest: BaseBoardGameTest() {

    @Test
    fun player1IsAbleToClaimTheAppropriateResourcesAfterSetup() {
        val deterministicDiceRoll = getDiceRollWithSpecifiedRollValue(1, 4, gameState, oracle)
        val rollDiceFlow = RollDiceFlow(gameState.linearId, deterministicDiceRoll)
        arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(rollDiceFlow, network)

        network.waitQuiescent()
        val txWithNewResources = arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(GatherResourcesFlow(gameBoardLinearId = gameState.linearId), network)
        requireThat {
            val resources = txWithNewResources.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }
    }

    @Test
    fun playersMustClaimResourcesOnTheirTurn() {
        val deterministicDiceRoll = getDiceRollWithSpecifiedRollValue(1, 4, gameState, oracle)
        val rollDiceFlow = RollDiceFlow(gameState.linearId, deterministicDiceRoll)
        arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(rollDiceFlow, network)
        arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(GatherResourcesFlow(gameBoardLinearId = gameState.linearId), network)

        assertFailsWith<FlowException>("The player who is proposing this transaction is not currently the player whose turn it is.") {
            arrayOfAllPlayerNodesInOrder[2].runFlowAndReturn(EndTurnFlow(gameState.linearId), network)
        }

    }

    @Test
    fun players123and4AreAbleToClaimTheAppropriateResourcesAfterSetup() {
        val randomDiceRoll1 = getDiceRollWithRandomRollValue(gameState, oracle)
        val rollDiceFlow1 = RollDiceFlow(gameState.linearId, randomDiceRoll1)
        arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(rollDiceFlow1, network)

        val txWithNewResourcesOwnedByPlayer1 = arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(GatherResourcesFlow(gameBoardLinearId = gameState.linearId), network)
        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer1.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }
        arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(EndTurnFlow(gameState.linearId), network)

        val randomDiceRoll2 = getDiceRollWithRandomRollValue(gameState, oracle)
        val rollDiceFlow2 = RollDiceFlow(gameState.linearId, randomDiceRoll2)
        arrayOfAllPlayerNodesInOrder[1].runFlowAndReturn(rollDiceFlow2, network)

        val txWithNewResourcesOwnedByPlayer2 = arrayOfAllPlayerNodesInOrder[1].runFlowAndReturn(GatherResourcesFlow(gameState.linearId), network)
        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer2.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }
        arrayOfAllPlayerNodesInOrder[1].runFlowAndReturn(EndTurnFlow(gameState.linearId), network)


        val randomDiceRoll3 = getDiceRollWithRandomRollValue(gameState, oracle)
        arrayOfAllPlayerNodesInOrder[2].runFlowAndReturn(RollDiceFlow(gameState.linearId, randomDiceRoll3), network)

        val txWithNewResourcesOwnedByPlayer3 = arrayOfAllPlayerNodesInOrder[2].runFlowAndReturn(GatherResourcesFlow(gameState.linearId), network)
        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer3.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }
        arrayOfAllPlayerNodesInOrder[2].runFlowAndReturn(EndTurnFlow(gameState.linearId), network)

        val randomDiceRoll4 = getDiceRollWithRandomRollValue(gameState, oracle)
        arrayOfAllPlayerNodesInOrder[3].runFlowAndReturn(RollDiceFlow(gameState.linearId, randomDiceRoll4), network)

        val txWithNewResourcesOwnedByPlayer4 = arrayOfAllPlayerNodesInOrder[3].runFlowAndReturn(GatherResourcesFlow(gameState.linearId), network)
        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer4.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

    }

}
