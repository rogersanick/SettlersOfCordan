package com.r3.cordan.primary.flows.development

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.primary.contracts.robber.PlayBlockerContract
import com.r3.cordan.primary.contracts.robber.RobberContract
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.states.resources.GameCurrencyState
import com.r3.cordan.primary.states.robber.BlockedStatus
import com.r3.cordan.primary.states.robber.PlayBlockerState
import com.r3.cordan.primary.states.robber.RobberState
import com.r3.cordan.primary.states.board.GameBoardState
import com.r3.cordan.primary.states.development.DevelopmentCardType
import com.r3.cordan.primary.states.development.FaceDownDevelopmentCardState
import com.r3.cordan.primary.states.development.RevealedDevelopmentCardState
import com.r3.cordan.primary.states.turn.TurnTrackerState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow(version = 1)
@StartableByRPC
class PlayDevelopmentCardFlow(private val revealedDevelopmentCardId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        // Retrieve the Development Card State from the vault.
        val revealedDevelopmentCardStateAndRef = serviceHub.vaultService
                .querySingleState<RevealedDevelopmentCardState>(revealedDevelopmentCardId)
        val revealedDevelopmentCardState = revealedDevelopmentCardStateAndRef.state.data

        // Retrieve the FaceDown development card state from the vault
        val faceDownDevelopmentCardStateAndRef = serviceHub.vaultService
                .querySingleState<FaceDownDevelopmentCardState>(revealedDevelopmentCardState.faceDownDevelopmentCardId)
        val faceDownDevelopmentCardState = faceDownDevelopmentCardStateAndRef.state.data

        // Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(faceDownDevelopmentCardState.gameBoardId)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Get a reference to the notary service on the network
        val notary = gameBoardStateAndRef.state.notary

        // Retrieve the Turn Tracker State from the vault
        val turnTrackerStateAndRef = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
        if (!gameBoardState.isValid(turnTrackerStateAndRef.state.data)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(turnTrackerStateAndRef)

        // Step 6. Create a transaction builder
        val tb = TransactionBuilder(notary)

        // Step 10. Verify and sign the transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 11. Collect Signatures on the transaction
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // Step 12. Finalize the transaction
        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(PlayDevelopmentCardFlow::class)
class PlayDevelopmentCardFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val gameBoardState = serviceHub.vaultService
                        .querySingleState<GameBoardState>(stx.coreTransaction.references)
                        .state.data

                val turnTrackerState = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(stx.coreTransaction.references)
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
                    throw IllegalArgumentException("Only the current player may propose the next move.")
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(
                otherSideSession = counterpartySession,
                expectedTxId = txWeJustSignedId.id,
                statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}
