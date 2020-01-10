package com.r3.cordan.primary.flows.development

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.primary.contracts.development.DevelopmentCardContract
import com.r3.cordan.primary.service.GenerateSpendService
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.states.development.DevelopmentCardState
import com.r3.cordan.primary.states.development.DevelopmentCardType
import com.r3.cordan.primary.states.structure.*
import com.r3.cordan.primary.states.turn.TurnTrackerState
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.newSecureRandom
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap


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

        // Step 1. Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data
        val gameBoardReferencedStateAndRef = ReferencedStateAndRef(gameBoardStateAndRef)

        // Step 2. Get a reference to the notary service on the network
        val notary = gameBoardStateAndRef.state.notary

        // Step 3. Retrieve the Turn Tracker State from the vault
        val turnTrackerStateAndRef = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
        if (!gameBoardState.isValid(turnTrackerStateAndRef.state.data)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(turnTrackerStateAndRef)

        // Step 5. Create a new transaction builder
        val tb = TransactionBuilder(notary)

        // Step 6. Request randomness from counter parties
        val committedRandomNums = arrayListOf<Pair<Int, DigitalSignature.WithKey>>()
        val seed = UniqueIdentifier().id.hashCode()
        gameBoardState.players.forEach { party ->
            val session = initiateFlow(party)
            committedRandomNums.add(session.sendAndReceive<Pair<Int, DigitalSignature.WithKey>>(seed).unwrap { it })
        }

        val devCardTypeIndex = committedRandomNums.sumBy { it.first } % 5
        val devCardType = DevelopmentCardType.values()[devCardTypeIndex]

        // Step 7. Create initial settlement
        val developmentCard = DevelopmentCardState(ourIdentity, devCardType)

        // Step 8. Add the appropriate resources to the transaction to pay for the Settlement.
        serviceHub.cordaService(GenerateSpendService::class.java)
                .generateInGameSpend(gameBoardLinearId, tb, getBuildableCosts(Buildable.DevelopmentCard), ourIdentity, ourIdentity)

        // Step 9. Add all states and commands to the transaction.
        tb.addReferenceState(gameBoardReferencedStateAndRef)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)
        tb.addOutputState(developmentCard)

        // Step 10. Create new commands for placing a settlement and ending a turn. Add both to the transaction.
        tb.addCommand(Command(DevelopmentCardContract.Commands.Issue(seed, committedRandomNums), listOf(ourIdentity.owningKey)))

        // Step 11. Sign initial transaction
        tb.verify(serviceHub)
        val stx = serviceHub.signInitialTransaction(tb)

        // Step 12. Finalize the TX
        return subFlow(FinalityFlow(stx, gameBoardState.players.map { initiateFlow(it) }))
    }
}

@InitiatedBy(BuildDevelopmentCardFlow::class)
class BuildDevelopmentCardFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val seed = counterpartySession.receive<Int>().unwrap { it }
        val randomInput = newSecureRandom().nextInt(10)
        val byteArrayOfDataToSign = byteArrayOf(seed.toByte(), randomInput.toByte())
        val ourSignatureOverData = serviceHub.keyManagementService.sign(byteArrayOfDataToSign, ourIdentity.owningKey)
        counterpartySession.send(Pair(randomInput, ourSignatureOverData))

        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val gameBoardState = stx.coreTransaction.outputsOfType<GameBoardState>().single()
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))

    }
}
