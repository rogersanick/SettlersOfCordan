package com.r3.cordan.primary.flows.development

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.primary.contracts.development.DevelopmentCardContract
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.states.development.RevealedDevelopmentCardState
import com.r3.cordan.primary.states.development.DevelopmentCardType
import com.r3.cordan.primary.states.board.GameBoardState
import com.r3.cordan.primary.states.turn.TurnTrackerState
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.newSecureRandom
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap


// *******************************
// * Reveal Development Card Flow *
// *******************************

/**
 * This is the flow nodes may execute to consume resources and issue a new development
 * card onto the ledger.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class RevealDevelopmentCardFlow(
        private val gameBoardLinearId: UniqueIdentifier,
        private val faceDownDevelopmentCardLinearID: UniqueIdentifier
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

        // Request randomness from counter parties
        val committedRandomNums = arrayListOf<Pair<Int, DigitalSignature.WithKey>>()
        val seed = UniqueIdentifier().id.hashCode()
        (gameBoardState.players - ourIdentity).forEach { party ->
            val session = initiateFlow(party)
            committedRandomNums.add(session.sendAndReceive<Pair<Int, DigitalSignature.WithKey>>(seed).unwrap { it })
        }

        val devCardTypeIndex = committedRandomNums.sumBy { it.first } % 5
        val devCardType = DevelopmentCardType.values()[devCardTypeIndex]

        // Create initial settlement
        val developmentCard = RevealedDevelopmentCardState(ourIdentity, devCardType, faceDownDevelopmentCardLinearID)

        // Add all states and commands to the transaction.
        tb.addReferenceState(gameBoardReferencedStateAndRef)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)
        tb.addOutputState(developmentCard)

        // Create new commands for placing a settlement and ending a turn. Add both to the transaction.
        tb.addCommand(Command(DevelopmentCardContract.Commands.Reveal(seed, committedRandomNums), listOf(ourIdentity.owningKey)))

        // Sign initial transaction
        tb.verify(serviceHub)
        val stx = serviceHub.signInitialTransaction(tb)

        // Finalize the TX
        return subFlow(FinalityFlow(stx, emptySet<FlowSession>()))
    }
}

@InitiatedBy(RevealDevelopmentCardFlow::class)
class RevealDevelopmentCardFlowResponder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val seed = counterpartySession.receive<Int>().unwrap { it }
        val randomInput = newSecureRandom().nextInt(10)
        val byteArrayOfDataToSign = byteArrayOf(seed.toByte(), randomInput.toByte())
        val ourSignatureOverData = serviceHub.keyManagementService.sign(byteArrayOfDataToSign, ourIdentity.owningKey)
        counterpartySession.send(Pair(randomInput, ourSignatureOverData))
    }
}
