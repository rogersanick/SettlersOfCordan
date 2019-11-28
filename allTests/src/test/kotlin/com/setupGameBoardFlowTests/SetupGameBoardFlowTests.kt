package com.setupGameBoardFlowTests

import com.contractsAndStates.contracts.GameStateContract
import com.contractsAndStates.states.GameBoardState
import com.flows.*
import com.testUtilities.BaseCordanTest
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentity
import org.junit.Test

class SetupGameBoardFlowTests: BaseCordanTest() {

    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger.
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val future = a.startFlow(gameStateIssueFlow);
        network.runNetwork()

        val ptx = future.getOrThrow()

        assert(ptx.tx.inputs.isEmpty())

        val gameBoardState = ptx.tx.outputs.filter { it.data is GameBoardState }.single().data
        assert(gameBoardState is GameBoardState)

        val command = ptx.tx.commands.filter { it.value is GameStateContract.Commands.SetUpGameBoard }.single()

        assert(command.value is GameStateContract.Commands.SetUpGameBoard)
        assert(command.signers.toSet() == gameBoardState.participants.map { it.owningKey }.toSet())

        ptx.verifySignaturesExcept(
                p2.owningKey,
                p3.owningKey,
                p4.owningKey,
                network.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey
        )
    }

    @Test
    fun flowReturnsCorrectlyFormedSignedTransaction() {

        // Get an identity for each of the players of the game.
        val p1 = a.info.chooseIdentity()
        val p2 = b.info.chooseIdentity()
        val p3 = c.info.chooseIdentity()
        val p4 = d.info.chooseIdentity()

        // Issue a game state onto the ledger.
        val gameStateIssueFlow = SetupGameBoardFlow(p1, p2, p3, p4)
        val future = a.startFlow(gameStateIssueFlow)
        network.runNetwork()

        val stx = future.getOrThrow()

        assert(stx.tx.inputs.isEmpty())

        val gameBoardState = stx.tx.outputs.filter { it.data is GameBoardState }.single().data
        assert(gameBoardState is GameBoardState)

        val command = stx.tx.commands.filter { it.value is GameStateContract.Commands.SetUpGameBoard }.single()

        assert(command.value is GameStateContract.Commands.SetUpGameBoard)
        assert(command.signers.toSet() == gameBoardState.participants.map { it.owningKey }.toSet())

        stx.verifyRequiredSignatures()
    }
}