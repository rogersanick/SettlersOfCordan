package com.r3.cordan.primary

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.cordan.primary.flows.turn.EndTurnFlow
import com.r3.cordan.primary.flows.resources.GatherResourcesFlow
import com.r3.cordan.primary.flows.dice.RollDiceFlow
import com.r3.cordan.primary.flows.board.SetupGameBoardFlow
import com.r3.cordan.primary.states.structure.GameBoardState
import com.r3.cordan.testutils.getDiceRollWithRandomRollValue
import com.r3.cordan.testutils.getDiceRollWithSpecifiedRollValue
import com.r3.cordan.testutils.setupGameBoardForTesting
import com.r3.cordan.testutils.BaseCordanTest
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowException
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentity
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ClaimResourcesFlowTest: BaseCordanTest() {

    @Test
    fun player1IsAbleToClaimTheAppropriateResourcesAfterSetup() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger
        val gameStateIssueFlow = (SetupGameBoardFlow(p1, p2, p3, p4))
        val futureWithGameState = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stxGameState = futureWithGameState.getOrThrow()

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        // Setup game board for testing
        val gameBoardState = setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder)

        val deterministicDiceRoll = getDiceRollWithSpecifiedRollValue(1, 4, gameBoardState, oracle)
        val rollDiceFlow = RollDiceFlow(gameBoardState.linearId, deterministicDiceRoll)
        val futureWithDiceRoll = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRoll.getOrThrow()

        val futureWithClaimedResources = arrayOfAllPlayerNodesInOrder[0].startFlow(GatherResourcesFlow(gameBoardLinearId = gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResources = futureWithClaimedResources.getOrThrow()

        requireThat {
            val resources = txWithNewResources.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }
    }

    @Test
    fun playersMustClaimResourcesOnTheirTurn() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger
        val gameStateIssueFlow = (SetupGameBoardFlow(p1, p2, p3, p4))
        val futureWithGameState = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stxGameState = futureWithGameState.getOrThrow()

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d);
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        // Setup game board for testing
        val gameBoardState = setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder)

        val deterministicDiceRoll = getDiceRollWithSpecifiedRollValue(1, 4, gameBoardState, oracle)
        val rollDiceFlow = RollDiceFlow(gameBoardState.linearId, deterministicDiceRoll)
        val futureWithDiceRollPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow)
        network.runNetwork()
        futureWithDiceRollPlayer1.getOrThrow()

        val futureWithClaimedResourcesByPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(GatherResourcesFlow(gameBoardLinearId = gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer1 = futureWithClaimedResourcesByPlayer1.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer1.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer2Turn = arrayOfAllPlayerNodesInOrder[2].startFlow(EndTurnFlow(gameBoardState.linearId))
        network.runNetwork()
        assertFailsWith<FlowException>("The player who is proposing this transaction is not currently the player whose turn it is.") { futureWithPlayer2Turn.getOrThrow() }

    }

    @Test
    fun players123and4AreAbleToClaimTheAppropriateResourcesAfterSetup() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger
        val gameStateIssueFlow = (SetupGameBoardFlow(p1, p2, p3, p4))
        val futureWithGameState = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stxGameState = futureWithGameState.getOrThrow()

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        // Setup game board for testing
        val gameBoardState = setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder)

        val randomDiceRoll1 = getDiceRollWithRandomRollValue(gameBoardState, oracle)
        val rollDiceFlow1 = RollDiceFlow(gameBoardState.linearId, randomDiceRoll1)
        val futureWithDiceRollPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(rollDiceFlow1)
        network.runNetwork()
        futureWithDiceRollPlayer1.getOrThrow()

        val futureWithClaimedResourcesByPlayer1 = arrayOfAllPlayerNodesInOrder[0].startFlow(GatherResourcesFlow(gameBoardLinearId = gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer1 = futureWithClaimedResourcesByPlayer1.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer1.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer2Turn = arrayOfAllPlayerNodesInOrder[0].startFlow(EndTurnFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer2Turn.getOrThrow()

        val randomDiceRoll2 = getDiceRollWithRandomRollValue(gameBoardState, oracle)
        val rollDiceFlow2 = RollDiceFlow(gameBoardState.linearId, randomDiceRoll2)
        val futureWithPlayer2DiceRoll = arrayOfAllPlayerNodesInOrder[1].startFlow(rollDiceFlow2)
        network.runNetwork()
        futureWithPlayer2DiceRoll.getOrThrow()

        val futureWithClaimedResourcesByPlayer2 = arrayOfAllPlayerNodesInOrder[1].startFlow(GatherResourcesFlow(gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer2 = futureWithClaimedResourcesByPlayer2.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer2.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer3Turn = arrayOfAllPlayerNodesInOrder[1].startFlow(EndTurnFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer3Turn.getOrThrow()

        val randomDiceRoll3 = getDiceRollWithRandomRollValue(gameBoardState, oracle)
        val futureWithPlayer3DiceRoll = arrayOfAllPlayerNodesInOrder[2].startFlow(RollDiceFlow(gameBoardState.linearId, randomDiceRoll3))
        network.runNetwork()
        futureWithPlayer3DiceRoll.getOrThrow()

        val futureWithClaimedResourcesByPlayer3 = arrayOfAllPlayerNodesInOrder[2].startFlow(GatherResourcesFlow(gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer3 = futureWithClaimedResourcesByPlayer3.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer3.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

        val futureWithPlayer4Turn = arrayOfAllPlayerNodesInOrder[2].startFlow(EndTurnFlow(gameBoardState.linearId))
        network.runNetwork()
        futureWithPlayer4Turn.getOrThrow()

        val randomDiceRoll4 = getDiceRollWithRandomRollValue(gameBoardState, oracle)
        val futureWithPlayer4DiceRoll = arrayOfAllPlayerNodesInOrder[3].startFlow(RollDiceFlow(gameBoardState.linearId, randomDiceRoll4))
        network.runNetwork()
        futureWithPlayer4DiceRoll.getOrThrow()

        val futureWithClaimedResourcesByPlayer4 = arrayOfAllPlayerNodesInOrder[3].startFlow(GatherResourcesFlow(gameBoardState.linearId))
        network.runNetwork()
        val txWithNewResourcesOwnedByPlayer4 = futureWithClaimedResourcesByPlayer4.getOrThrow()

        requireThat {
            val resources = txWithNewResourcesOwnedByPlayer4.coreTransaction.outputsOfType<FungibleToken>()
            "Assert that between 0 and 6 resources were produced in the transaction" using (resources.size in 0..6)
        }

    }

}
