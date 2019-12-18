package com.r3.cordan.primary.flows.win

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.primary.contracts.board.GameStateContract
import com.r3.cordan.primary.flows.queryBelongsToGameBoard
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.states.structure.GameBoardState
import com.r3.cordan.primary.states.structure.SettlementState
import com.r3.cordan.primary.states.turn.TurnTrackerState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// ******************
// * Claim Win Flow *
// ******************

/**
 * This flow can be used by any player with a valid claim as victor of the game to end the game instance
 * and irrevocably (and immutably) establish themselves as the winner.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class ClaimWinFlow(val gameBoardLinearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 2. Get a reference to the notary service on the network
        val notary = gameBoardStateAndRef.state.notary

        // Step 3. Retrieve the Turn Tracker State from the vault
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId))
        if (!gameBoardState.isValid(turnTrackerReferenceStateAndRef.stateAndRef.state.data)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }

        // Step 4. Retrieve all of our settlement states from the vault.
        val settlementStatesWeOwn = serviceHub.vaultService
                .queryBelongsToGameBoard<SettlementState>(gameBoardLinearId)
                .filter { it -> it.state.data.owner == ourIdentity }
                .map { ReferencedStateAndRef(it) }

        // Step 5. Create a new transaction builder
        val tb = TransactionBuilder(notary)

        // Step 6. Add all states and commands to the transaction.
        tb.addInputState(gameBoardStateAndRef)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)
        settlementStatesWeOwn.forEach { tb.addReferenceState(it) }
        tb.addOutputState(gameBoardState.weWin(ourIdentity))
        tb.addCommand(GameStateContract.Commands.WinGame())

        // Step 7. Sign initial transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 8. Collect all signatures
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(ClaimWinFlow::class)
class ClaimWinFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val gameBoardState = stx.coreTransaction.outputsOfType<GameBoardState>().first()
                val turnTrackerStateRef = stx.coreTransaction.references.single()
                val turnTrackerState = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(turnTrackerStateRef)
                        .state.data

                val lastTurnTrackerOnRecordStateAndRef = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(turnTrackerState.linearId)
                        .state.data
                if (lastTurnTrackerOnRecordStateAndRef.linearId != turnTrackerState.linearId) {
                    throw FlowException("The TurnTracker included in the transaction is not correct for this game or turn.")
                }
                if (!gameBoardState.isValid(lastTurnTrackerOnRecordStateAndRef)) {
                    throw FlowException("The turn tracker state does not point back to the GameBoardState")
                }

                if (counterpartySession.counterparty.owningKey !=
                        gameBoardState.players[lastTurnTrackerOnRecordStateAndRef.currTurnIndex].owningKey) {
                    throw FlowException("Only the current player may propose the next move.")
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}
