package com.flows

import com.contractsAndStates.contracts.RobberContract
import com.oracleClientStatesAndContracts.states.DiceRollState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow(version = 1)
@StartableByRPC
class TriggerRobberFlow(val gameBoardLinearId: UniqueIdentifier,
                        val updatedRobberLocation: Int): FlowLogic<SignedTransaction>() {
    override fun call(): SignedTransaction {

        // Step 1. Get a reference to the notary service on the network
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = getGameBoardStateFromLinearID(gameBoardLinearId, serviceHub)
        val gameBoardState = gameBoardStateAndRef.state.data
        val gameBoardReferenceStateAndRef = ReferencedStateAndRef(gameBoardStateAndRef)

        // Step 3. Retrieve the Turn Tracker State from the vault
        val turnTrackerStateAndRef = getTurnTrackerStateFromLinearID(gameBoardState.linearId, serviceHub)
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(turnTrackerStateAndRef)

        // Step 4. Retrieve the Dice Roll State from the vault
        val diceRollStateAndRef = serviceHub.vaultService.queryBy<DiceRollState>().states.filter { it.state.data.gameBoardStateUniqueIdentifier == gameBoardLinearId }.single()

        // Step 5. Add the existing robber state as an input state
        val robberStateAndRef = getRobberStateFromLinearID(gameBoardState.linearId, serviceHub)

        // Step 6. Create a new robber state
        val movedRobberState = robberStateAndRef.state.data.move(updatedRobberLocation)

        // Step 7. Create the appropriate command
        val command = RobberContract.Commands.MoveRobber()

        // Step 8. Create a transaction builder and add all input/output states
        val tb = TransactionBuilder(notary)
        tb.addInputState(robberStateAndRef)
        tb.addInputState(diceRollStateAndRef)
        tb.addOutputState(movedRobberState)
        tb.addReferenceState(gameBoardReferenceStateAndRef)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)
        tb.addCommand(command, gameBoardState.players.map { it.owningKey })

        // Step 9. Verify and sign the transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 10. Collect Signatures on the transaction
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // Step 11. Finalize the transaction
        return subFlow(FinalityFlow(stx, sessions))
    }
}
