package com.r3.cordan.primary.flows.development

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.primary.contracts.development.DevelopmentCardContract
import com.r3.cordan.primary.service.GenerateSpendService
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.states.board.GameBoardState
import com.r3.cordan.primary.states.development.FaceDownDevelopmentCardState
import com.r3.cordan.primary.states.structure.*
import com.r3.cordan.primary.states.turn.TurnTrackerState
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder


// *******************************
// * Build Development Card Flow *
// *******************************

/**
 * This is the flow nodes may execute to consume resources and issue a new development
 * card onto the ledger.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class BuildDevelopmentCardFlow(
        val gameBoardLinearId: UniqueIdentifier
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        // Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data
        val gameBoardReferencedStateAndRef = ReferencedStateAndRef(gameBoardStateAndRef)

        // Get a reference to the notary service on the network
        val notary = gameBoardStateAndRef.state.notary

        // Retrieve the Turn Tracker State from the vault
        val turnTrackerStateAndRef = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
        if (!gameBoardState.isValid(turnTrackerStateAndRef.state.data)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(turnTrackerStateAndRef)

        // Create a new transaction builder
        val tb = TransactionBuilder(notary)

        // Create face down development card
        val developmentCard = FaceDownDevelopmentCardState(ourIdentity, gameBoardState.players, gameBoardLinearId)

        // Add the appropriate resources to the transaction to pay for the Settlement.
        serviceHub.cordaService(GenerateSpendService::class.java)
                .generateInGameSpend(gameBoardLinearId, tb, getBuildableCosts(Buildable.DevelopmentCard), ourIdentity, ourIdentity)

        // Add all states and commands to the transaction.
        tb.addReferenceState(gameBoardReferencedStateAndRef)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)
        tb.addOutputState(developmentCard)

        // Create new commands for placing a settlement and ending a turn. Add both to the transaction.
        tb.addCommand(Command(DevelopmentCardContract.Commands.Issue(), gameBoardState.playerKeys()))

        // Sign initial transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Collect signatures
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // Step 12. Finalize the TX
        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(BuildDevelopmentCardFlow::class)
class BuildDevelopmentCardFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // TODO: Add turn check
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))

    }
}
