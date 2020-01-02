package com.r3.cordan.primary

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.cordan.primary.flows.turn.EndTurnFlow
import com.r3.cordan.primary.flows.resources.GatherResourcesFlow
import com.r3.cordan.primary.flows.dice.RollDiceFlow
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
        val futureWithDiceRoll = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRoll.getOrThrow()

        val futureWithClaimedResources = arrayOfAllPlayerNodesInOrder[0].startFlow(GatherResourcesFlow(gameBoardLinearId = gameState.linearId))
        network.runNetwork()
        val txWithNewResources = futureWithClaimedResources.getOrThrow()

        requireThat {
            val resources = txWithNewResources.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }
    }

    @Test
    fun playersMustClaimResourcesOnTheirTurn() {

        val deterministicDiceRoll = getDiceRollWithSpecifiedRollValue(1, 4, gameState, oracle)
        val rollDiceFlow = RollDiceFlow(gameState.linearId, deterministicDiceRoll)
        val futureWithDiceRollPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRollPlayer1.getOrThrow()

        val futureWithClaimedResourcesByPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(GatherResourcesFlow(gameBoardLinearId = gameState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer1 = futureWithClaimedResourcesByPlayer1.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer1.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer2Turn = arrayOfAllPlayerNodesInOrder[2].startFlow(EndTurnFlow(gameState.linearId))
        network.runNetwork()
        assertFailsWith<FlowException>("The player who is proposing this transaction is not currently the player whose turn it is.") { futureWithPlayer2Turn.getOrThrow() }

    }

    @Test
    fun players123and4AreAbleToClaimTheAppropriateResourcesAfterSetup() {

        val randomDiceRoll1 = getDiceRollWithRandomRollValue(gameState, oracle)
        val rollDiceFlow1 = RollDiceFlow(gameState.linearId, randomDiceRoll1)
        val futureWithDiceRollPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow1)
        network.runNetwork()
        futureWithDiceRollPlayer1.getOrThrow()

        val futureWithClaimedResourcesByPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(GatherResourcesFlow(gameBoardLinearId = gameState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer1 = futureWithClaimedResourcesByPlayer1.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer1.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer2Turn = arrayOfAllPlayerNodesInOrder[0].startFlow(EndTurnFlow(gameState.linearId))
        network.runNetwork()
        futureWithPlayer2Turn.getOrThrow()

        val randomDiceRoll2 = getDiceRollWithRandomRollValue(gameState, oracle)
        val rollDiceFlow2 = RollDiceFlow(gameState.linearId, randomDiceRoll2)
        val futureWithPlayer2DiceRoll = arrayOfAllPlayerNodesInOrder[1].startFlow(rollDiceFlow2)
        network.runNetwork()
        futureWithPlayer2DiceRoll.getOrThrow()

        val futureWithClaimedResourcesByPlayer2 = arrayOfAllPlayerNodesInOrder[1].startFlow(GatherResourcesFlow(gameState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer2 = futureWithClaimedResourcesByPlayer2.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer2.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer3Turn = arrayOfAllPlayerNodesInOrder[1].startFlow(EndTurnFlow(gameState.linearId))
        network.runNetwork()
        futureWithPlayer3Turn.getOrThrow()

        val randomDiceRoll3 = getDiceRollWithRandomRollValue(gameState, oracle)
        val futureWithPlayer3DiceRoll = arrayOfAllPlayerNodesInOrder[2].startFlow(RollDiceFlow(gameState.linearId, randomDiceRoll3))
        network.runNetwork()
        futureWithPlayer3DiceRoll.getOrThrow()

        val futureWithClaimedResourcesByPlayer3 = arrayOfAllPlayerNodesInOrder[2].startFlow(GatherResourcesFlow(gameState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer3 = futureWithClaimedResourcesByPlayer3.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer3.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer4Turn = arrayOfAllPlayerNodesInOrder[2].startFlow(EndTurnFlow(gameState.linearId))
        network.runNetwork()
        futureWithPlayer4Turn.getOrThrow()

        val randomDiceRoll4 = getDiceRollWithRandomRollValue(gameState, oracle)
        val futureWithPlayer4DiceRoll = arrayOfAllPlayerNodesInOrder[3].startFlow(RollDiceFlow(gameState.linearId, randomDiceRoll4))
        network.runNetwork()
        futureWithPlayer4DiceRoll.getOrThrow()

        val futureWithClaimedResourcesByPlayer4 = arrayOfAllPlayerNodesInOrder[3].startFlow(GatherResourcesFlow(gameState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer4 = futureWithClaimedResourcesByPlayer4.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer4.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

    }

}
