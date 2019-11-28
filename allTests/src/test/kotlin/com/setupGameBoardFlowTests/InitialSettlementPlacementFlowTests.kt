package com.setupGameBoardFlowTests

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.HexTileIndex
import com.contractsAndStates.states.HexTileType
import com.flows.BuildInitialSettlementAndRoadFlow
import com.flows.SetupGameBoardFlow
import com.gameUtilities.placeAPieceFromASpecificNodeAndEndTurn
import com.gameUtilities.setupGameBoardForTesting
import com.testUtilities.BaseCordanTest
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowException
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentity
import org.junit.Test
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
        val gameStateIssueFlow = (SetupGameBoardFlow(p1, p2, p3, p4))
        val futureWithGameState = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stxGameState = futureWithGameState.getOrThrow()

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()

        // Build an initial settlement by issuing a settlement state
        // and updating the current turn.
        val hexTileIndex = if (gameState.hexTiles.get(HexTileIndex(0)).resourceType == HexTileType.Desert) 1 else 2
        val buildInitialSettlementFlow = BuildInitialSettlementAndRoadFlow(gameState.linearId, hexTileIndex, 5, 5)
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }
        val futureWithInitialSettlementBuild = arrayOfAllPlayerNodesInOrder.first().startFlow(buildInitialSettlementFlow)
        network.runNetwork()

        val stxBuildInitialSettlement = futureWithInitialSettlementBuild.getOrThrow()

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
        val gameStateIssueFlow = (SetupGameBoardFlow(p1, p2, p3, p4))
        val futureWithGameState = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stxGameState = futureWithGameState.getOrThrow()

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()

        val arrayOfAllTransactions = arrayListOf<SignedTransaction>()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d);
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions)

    }

    @Test(expected = TransactionVerificationException::class)
    fun concurrentFlowsAreUnableToPlaceInvalidPieces() {

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

        val arrayOfAllTransactions = arrayListOf<SignedTransaction>()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d);
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayListOf(Pair(0, 5), Pair(0, 4), Pair(0, 3), Pair(1, 1))
        val nonconflictingHextileIndexAndCoordinatesRound2 = arrayListOf(Pair(1, 3), Pair(2, 1), Pair(2, 3), Pair(3, 3))

        for (i in 0..3) {
            placeAPieceFromASpecificNodeAndEndTurn(i, nonconflictingHextileIndexAndCoordinatesRound1, gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions, false)
        }

        for (i in 3.downTo(0)) {
            placeAPieceFromASpecificNodeAndEndTurn(i, nonconflictingHextileIndexAndCoordinatesRound2, gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions, false)
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
        val gameStateIssueFlow = (SetupGameBoardFlow(p1, p2, p3, p4))
        val futureWithGameState = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stxGameState = futureWithGameState.getOrThrow()

        // Get a reference to the issued game state
        val gameState = stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()

        val arrayOfAllTransactions = arrayListOf<SignedTransaction>()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

        setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions)

        assertFailsWith<FlowException>("You should be using the end turn function") { placeAPieceFromASpecificNodeAndEndTurn(0, arrayListOf(Pair(0, 0)), gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions, false) }

    }

    @Test
    fun playersMustBuildSettlementsAccordingToTheTurnOrder() {

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

        val arrayOfAllTransactions = arrayListOf<SignedTransaction>()
        val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d);
        val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }
        val nonconflictingHextileIndexAndCoordinatesRound1 = arrayListOf(Pair(0, 5), Pair(0, 1), Pair(0, 3), Pair(1, 1))


        placeAPieceFromASpecificNodeAndEndTurn(0, nonconflictingHextileIndexAndCoordinatesRound1, gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions, false)
        assertFailsWith<FlowException>("Only the current player may propose the nextmove.") { placeAPieceFromASpecificNodeAndEndTurn(2, nonconflictingHextileIndexAndCoordinatesRound1, gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions, false) }

    }

}
