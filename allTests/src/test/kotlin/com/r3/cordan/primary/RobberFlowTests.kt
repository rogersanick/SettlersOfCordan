package com.r3.cordan.primary

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.cordan.primary.flows.board.SetupGameBoardFlow
import com.r3.cordan.primary.flows.random.RollDiceFlow
import com.r3.cordan.primary.flows.robber.ApplyRobberFlow
import com.r3.cordan.primary.flows.robber.MoveRobberFlow
import com.r3.cordan.primary.flows.robber.RemovePlayBlockerFlow
import com.r3.cordan.primary.states.board.HexTileIndex
import com.r3.cordan.primary.states.robber.PlayBlockerState
import com.r3.cordan.primary.states.robber.RobberState
import com.r3.cordan.primary.states.board.GameBoardState
import com.r3.cordan.testutils.*
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.requireThat
import net.corda.core.node.services.queryBy
import net.corda.testing.internal.chooseIdentity
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class RobberFlowTests: BaseCordanTest() {

    @Test
    fun player1IsUnableToMoveTheRobberWhenA7IsNotRolled() {

        // Issue a game state onto the ledger.
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val ptx = a.runFlowAndReturn(gameStateIssueFlow, network)
        val gameState = ptx.tx.outputs.filter{ it.data is GameBoardState }.single().data as GameBoardState
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTestingAndReturnIds(
                gameState,
                network,
                arrayOfAllPlayerNodesInOrder
        )

        val deterministicDiceRoll = getDiceRollWithSpecifiedRollValue(3, 2, gameState, oracle)
        val rollDiceFlow = RollDiceFlow(gameState.linearId, deterministicDiceRoll)
        arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(rollDiceFlow, network)

        assertFailsWith<TransactionVerificationException.ContractRejection>("The associated random roll must have a value of 7.") {
            arrayOfAllPlayerNodesInOrder[0]
                    .runFlowAndReturn(MoveRobberFlow(gameState.linearId, 5), network)
        }
    }

    @Test
    fun player1IsAbleToMoveTheRobberWhenA7IsRolled() {

        // Issue a game state onto the ledger.
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val ptx = a.runFlowAndReturn(gameStateIssueFlow, network)
        val gameState = ptx.tx.outputs.filter{ it.data is GameBoardState }.single().data as GameBoardState
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTestingAndReturnIds(
                gameState,
                network,
                arrayOfAllPlayerNodesInOrder
        )

        val deterministicDiceRoll = getDiceRollWithSpecifiedRollValue(3, 4, gameState, oracle)
        val rollDiceFlow = RollDiceFlow(gameState.linearId, deterministicDiceRoll)
        arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(rollDiceFlow, network)

        val stxWithMovedRobber = arrayOfAllPlayerNodesInOrder[0]
                .runFlowAndReturn(MoveRobberFlow(gameState.linearId, 5), network)

        requireThat {
            val outputRobber = stxWithMovedRobber.coreTransaction.outputsOfType<RobberState>().first()
            "Assert that the robber has been moved to the appropriate position" using (outputRobber.hexTileIndex == HexTileIndex(5))
        }
    }

    @Test
    fun player1IsAbleToApplyTheRobberAfterMovingIt() {

        // Issue a game state onto the ledger.
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val ptx = a.runFlowAndReturn(gameStateIssueFlow, network)
        val gameState = ptx.tx.outputs.filter{ it.data is GameBoardState }.single().data as GameBoardState
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTestingAndReturnIds(
                gameState,
                network,
                arrayOfAllPlayerNodesInOrder
        )

        // TODO: Ensure robber cannot be moved until setup is complete
        val deterministicDiceRoll = getDiceRollWithSpecifiedRollValue(3, 4, gameState, oracle)
        val rollDiceFlow = RollDiceFlow(gameState.linearId, deterministicDiceRoll)
        arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(rollDiceFlow, network)

        arrayOfAllPlayerNodesInOrder[0]
                .runFlowAndReturn(MoveRobberFlow(gameState.linearId, 5), network)

        val txWithAppliedRobber = arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(ApplyRobberFlow(gameState.linearId), network).tx

        val inputRobber = txWithAppliedRobber.outputsOfType<RobberState>().single()
        val outputRobber = txWithAppliedRobber.outputsOfType<RobberState>().single()

        requireThat {
            "The robber that was deactivated is the robber that was moved" using (outputRobber.linearId == inputRobber.linearId)
            "The robber has no changed position" using (outputRobber.hexTileIndex == inputRobber.hexTileIndex)
            "The output Robber has been deactivated" using (!outputRobber.active)
        }

    }

    @Test
    fun aPlayerIsAbleToRemoveAPlayBlockerState() {

        // Issue a game state onto the ledger.
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val ptx = a.runFlowAndReturn(gameStateIssueFlow, network)
        val gameState = ptx.tx.outputs.filter{ it.data is GameBoardState }.single().data as GameBoardState
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTestingAndReturnIds(
                gameState,
                network,
                arrayOfAllPlayerNodesInOrder
        )

        val nodeWithMoreThan7 = gatherUntilAPlayerHasMoreThan7(gameState, arrayOfAllPlayerNodesInOrder, oracle, network)

        val diceRollTriggeringRobber = getDiceRollWithSpecifiedRollValue(3, 4, gameState, oracle)
        arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(RollDiceFlow(gameState.linearId, diceRollTriggeringRobber), network)
        arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(MoveRobberFlow(gameState.linearId, 5), network)
        val txWithAppliedRobber = arrayOfAllPlayerNodesInOrder[0].runFlowAndReturn(ApplyRobberFlow(gameState.linearId), network).coreTransaction

        val playBlockerState = txWithAppliedRobber.outputsOfType<PlayBlockerState>()
                .filter { it.playerBlocked == nodeWithMoreThan7.info.legalIdentities.first() }
                .first()

        var resourceTotal = 0
        val resourcesToSpend = mutableMapOf<TokenType, Long>()
        val playerResources = countAllResourcesForASpecificNode(nodeWithMoreThan7).mutableMap
        playerResources.forEach {
            if (resourceTotal < playBlockerState.price) {
                if (resourceTotal + it.value > playBlockerState.price) {
                    val amount = it.value + resourceTotal.toLong() - playBlockerState.price
                    resourcesToSpend[it.key] = amount
                    resourceTotal += amount.toInt()
                }
                else {
                    resourcesToSpend[it.key] = it.value
                    resourceTotal += it.value.toInt()
                }
            }
        }

        nodeWithMoreThan7.runFlowAndReturn(RemovePlayBlockerFlow(playBlockerState.linearId, resourcesToSpend), network)
        network.waitQuiescent()

        requireThat {
            "All nodes now recognize that the nodeWithMoreThan7 has removed its playBlocker" using (
                    arrayOfAllPlayerNodesInOrder.all { it.services.vaultService.queryBy<PlayBlockerState>().states.filter { it.state.data.playerBlocked == nodeWithMoreThan7.info.legalIdentities.first() }.isEmpty() })
        }
    }

}