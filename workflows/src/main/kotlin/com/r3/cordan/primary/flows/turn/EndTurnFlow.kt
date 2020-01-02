package com.r3.cordan.primary.flows.turn

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.primary.contracts.turn.TurnTrackerContract
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.states.structure.GameBoardState
import com.r3.cordan.primary.states.turn.TurnTrackerState
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// *****************
// * End Turn Flow *
// *****************

/**
 * This flow allows the player to end their turn, update the turn tracker. It also
 * signals to the next player that it is now their turn.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class EndTurnFlow(val gameBoardStateLinearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Get a reference to the notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Retrieve the TurnTracker State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardStateLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 3. Retrieve the TurnTracker State from the vault.
        val turnTrackerStateAndRef = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
        val turnTrackerState = turnTrackerStateAndRef.state.data
        if (!gameBoardState.isValid(turnTrackerState)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }

        // Step 4. Create a new transaction builder using the notary
        val tb = TransactionBuilder(notary)

        // Step 5. Create a new command for ending our turns
        val endTurnCommand = Command(
                TurnTrackerContract.Commands.EndTurn(),
                gameBoardState.playerKeys())
        tb.addCommand(endTurnCommand)

        // Step 6. Add the old turn tracker state as an input.
        tb.addInputState(turnTrackerStateAndRef)

        // Step 7. Create the new turn tracker state and add it as an output.
        tb.addOutputState(turnTrackerState.endTurn())

        // Step 8. Add the game board state as a reference
        tb.addReferenceState(ReferencedStateAndRef(gameBoardStateAndRef))

        // Step 9. Verify and then sign the initial transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 10. Gather signatures from relevant parties (all other players)
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // Step 11. Run the FinalityFlow
        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(EndTurnFlow::class)
class EndTurnFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val inputTurnTrackerInTransaction = stx.coreTransaction.inputs.single()
                val turnTrackerReferencedInTransaction = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(inputTurnTrackerInTransaction)
                        .state.data

                val gameBoardStateRef = stx.coreTransaction.references.single()
                val gameBoard = serviceHub.vaultService
                        .querySingleState<GameBoardState>(gameBoardStateRef)
                        .state.data
                val lastTurnTrackerWeHaveOnRecord = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(gameBoard.turnTrackerLinearId)
                        .state.data
                if (lastTurnTrackerWeHaveOnRecord.linearId != turnTrackerReferencedInTransaction.linearId) {
                    throw IllegalArgumentException("The TurnTracker included in the transaction is not correct for this game or turn.")
                }
                if (!gameBoard.isValid(lastTurnTrackerWeHaveOnRecord)) {
                    throw FlowException("The turn tracker state does not point back to the GameBoardState")
                }
                if (lastTurnTrackerWeHaveOnRecord.currTurnIndex != turnTrackerReferencedInTransaction.currTurnIndex) {
                    throw IllegalArgumentException("The player is proposing an incorrect turn advancement.")
                }
                if (gameBoard.players[turnTrackerReferencedInTransaction.currTurnIndex] != counterpartySession.counterparty) {
                    throw IllegalArgumentException("The player who is proposing this transaction is not currently the player whose turn it is.")
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}
