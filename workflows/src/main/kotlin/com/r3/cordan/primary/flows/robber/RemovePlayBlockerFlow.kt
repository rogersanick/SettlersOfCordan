package com.r3.cordan.primary.flows.robber

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.primary.contracts.robber.PlayBlockerContract
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.service.GenerateSpendService
import com.r3.cordan.primary.states.structure.GameBoardState
import com.r3.cordan.primary.states.robber.PlayBlockerState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow(version = 1)
@StartableByRPC
class RemovePlayBlockerFlow(val playBlockerLinearId: UniqueIdentifier, val resourcesToDiscard: Map<TokenType, Long>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Retrieve the Game Board State from the vault.
        val playBlockerStateAndRef = serviceHub.vaultService
                .querySingleState<PlayBlockerState>(playBlockerLinearId)
        val playBlockerState = playBlockerStateAndRef.state.data

        // Step 2. Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(playBlockerState.gameBoardLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data
        val gameBoardReferenceStateAndRef = ReferencedStateAndRef(gameBoardStateAndRef)

        // Step 3. Get a reference to the notary service on the network
        val notary = gameBoardStateAndRef.state.notary

        // Step 4. Create a transaction builder
        val tb = TransactionBuilder(notary)

        // Step 5. Add resources to pay off the play blocker state
        serviceHub.cordaService(GenerateSpendService::class.java)
                .generateInGameSpend(tb, resourcesToDiscard, ourIdentity, ourIdentity)

        // Step 6. Create the appropriate command
        val removePlayBlockerCommand = PlayBlockerContract.Commands.RemovePlayBlocker()

        // Step 7. Add all input/output states
        tb.addInputState(playBlockerStateAndRef)
        tb.addReferenceState(gameBoardReferenceStateAndRef)
        tb.addCommand(removePlayBlockerCommand, gameBoardState.playerKeys())

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

@InitiatedBy(RemovePlayBlockerFlow::class)
class RemovePlayBlockerFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(
                otherSideSession = counterpartySession,
                expectedTxId = txWeJustSignedId.id,
                statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}
