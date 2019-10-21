package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.TurnTrackerContract
import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.TurnTrackerState
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// ******************************************
// * End Turn During Initial Settlement Flow *
// ******************************************

/**
 * This flow ends a players turn, but may only be used during the setup phase of
 * the game. Its impact on the proposed TurnTracker is different than the endTurnFlow
 * used in a game with the setup phase complete.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class EndTurnDuringInitialPlacementFlow(val gameBoardStateLinearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Retrieve the TurnTracker State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardStateLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 2. Get a reference to the notary
        val notary = gameBoardStateAndRef.state.notary

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
                TurnTrackerContract.Commands.EndTurnDuringInitialPlacementPhase(),
                gameBoardState.playerKeys())
        tb.addCommand(endTurnCommand)

        // Step 6. Add the old turn tracker state as an input.
        tb.addInputState(turnTrackerStateAndRef)

        // Step 7. Create the new turn tracker state and add it as an output.
        tb.addOutputState(turnTrackerState.endTurnDuringInitialSettlementPlacement())

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

@InitiatedBy(EndTurnDuringInitialPlacementFlow::class)
class EndTurnDuringInitialPlacementFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
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
                    throw FlowException("The TurnTracker included in the transaction is not correct for this game or turn.")
                }
                if (!gameBoard.isValid(lastTurnTrackerWeHaveOnRecord)) {
                    throw FlowException("The turn tracker state does not point back to the GameBoardState")
                }

                if (lastTurnTrackerWeHaveOnRecord.currTurnIndex != turnTrackerReferencedInTransaction.currTurnIndex) {
                    throw FlowException("The player is proposing an incorrect turn advancement.")
                }

                if (gameBoard.players[turnTrackerReferencedInTransaction.currTurnIndex] != counterpartySession.counterparty) {
                    throw FlowException("The player who is proposing this transaction is not currently the player whose turn it is.")
                }

                if (lastTurnTrackerWeHaveOnRecord.setUpRound2Complete) {
                    throw FlowException("You should be using the end turn function")
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}
