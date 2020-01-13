package com.r3.cordan.primary

import com.r3.cordan.primary.flows.structure.BuildInitialSettlementAndRoadFlow
import com.r3.cordan.primary.flows.board.SetupGameBoardFlow
import com.r3.cordan.primary.states.structure.GameBoardState
import com.r3.cordan.primary.states.board.HexTileIndex
import com.r3.cordan.primary.states.resources.HexTileType
import com.r3.cordan.testutils.*
import net.corda.core.flows.FlowException
import net.corda.testing.internal.chooseIdentity
import org.junit.jupiter.api.Test
import java.lang.IllegalArgumentException
import kotlin.test.assertFailsWith

class InitialSettlementPlacementFlowTests: BaseCordanTest() {

    @Test
    fun flowReturnsCorrectlyFormedFullySignedTransaction() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val stxGameState = a.runFlowAndReturn(gameStateIssueFlow, network)

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()

        // Build an initial settlement by issuing a settlement state
        // and updating the current turn.
        val hexTileIndex = if (gameState.hexTiles.get(HexTileIndex(0)).resourceType == HexTileType.Desert) 1 else 2
        val buildInitialSettlementFlow = BuildInitialSettlementAndRoadFlow(gameState.linearId, hexTileIndex, 5, 5)
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }
        val stxBuildInitialSettlement = arrayOfAllPlayerNodesInOrder
                .first().runFlowAndReturn(buildInitialSettlementFlow, network)

        assert(stxBuildInitialSettlement.tx.inputs.size == 1)
        assert(stxBuildInitialSettlement.tx.outputs.size == 3)

        val buildCommands = stxBuildInitialSettlement.tx.commands
        assert(buildCommands.first().signers.toSet() == gameState.players.map { it.owningKey }.toSet())
        stxBuildInitialSettlement.verifyRequiredSignatures()
    }

    @Test
    fun concurrentFlowsAreAbleToPlaceAllPieces() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val stxGameState = a.runFlowAndReturn(gameStateIssueFlow, network)

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()

        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder)

    }

    @Test
    fun concurrentFlowsAreUnableToPlaceInvalidPieces() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val stxGameState = a.runFlowAndReturn(gameStateIssueFlow, network)

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayListOf(Pair(0, 0), Pair(0, 0), Pair(0, 0), Pair(0, 0))

        placeAPieceFromASpecificNodeAndEndTurn(0, nonconflictingHextileIndexAndCoordinatesRound1, gameState, network, arrayOfAllPlayerNodesInOrder, false)
        assertFailsWith<IllegalArgumentException>("The flow should fail when constructing a new GameBoardState.") {
            placeAPieceFromASpecificNodeAndEndTurn(1, nonconflictingHextileIndexAndCoordinatesRound1, gameState, network, arrayOfAllPlayerNodesInOrder, false)
        }

    }

    @Test
    fun playersAreUnableToBuildSettlementUsingThePlaceInitialSettlementFlowAfterSetup() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val stxGameState = a.runFlowAndReturn(gameStateIssueFlow, network)

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()

        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder)

        assertFailsWith<FlowException>("You should be using the end turn function") { placeAPieceFromASpecificNodeAndEndTurn(0, arrayListOf(Pair(0, 0)), gameState, network, arrayOfAllPlayerNodesInOrder, false) }

    }

    @Test
    fun playersMustBuildSettlementsAccordingToTheTurnOrder() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val stxGameState = a.runFlowAndReturn(gameStateIssueFlow, network)

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()

        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayListOf(Pair(0, 5), Pair(0, 1), Pair(0, 3), Pair(1, 1))


        placeAPieceFromASpecificNodeAndEndTurn(0, nonconflictingHextileIndexAndCoordinatesRound1, gameState, network, arrayOfAllPlayerNodesInOrder, false)
        assertFailsWith<FlowException>("Only the current player may propose the nextmove.") { placeAPieceFromASpecificNodeAndEndTurn(2, nonconflictingHextileIndexAndCoordinatesRound1, gameState, network, arrayOfAllPlayerNodesInOrder, false) }

    }

}
