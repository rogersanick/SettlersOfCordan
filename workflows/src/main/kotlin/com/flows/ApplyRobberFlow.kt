package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.PlayBlockerContract
import com.contractsAndStates.contracts.RobberContract
import com.contractsAndStates.states.*
import com.r3.corda.lib.tokens.workflows.utilities.toParty
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.internal.ResolveTransactionsFlow
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow(version = 1)
@StartableByRPC
class ApplyRobberFlow(val gameBoardLinearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data
        val gameBoardReferenceStateAndRef = ReferencedStateAndRef(gameBoardStateAndRef)

        // Step 2. Get a reference to the notary service on the network
        val notary = gameBoardStateAndRef.state.notary

        // Step 3. Retrieve the Turn Tracker State from the vault
        val turnTrackerStateAndRef = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
        if (!gameBoardState.isValid(turnTrackerStateAndRef.state.data)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(turnTrackerStateAndRef)

        // Step 4. Add the existing robber state as an input state
        val robberStateAndRef = serviceHub.vaultService
                .querySingleState<RobberState>(gameBoardState.robberLinearId)
        val robber = robberStateAndRef.state.data
        if (!gameBoardState.isValid(robberStateAndRef.state.data)) {
            throw FlowException("The robber state does not point back to the GameBoardState")
        }
        if (!robber.active) {
            throw FlowException("The robber state is not active")
        }

        // Step 5. Create a new robber state
        val deactivatedRobberState = robberStateAndRef.state.data.deactivate()

        // Step 6. Create a transaction builder
        val tb = TransactionBuilder(notary)

        // Step 7. Add PlayBlockerStates for each of the players with more than 7 resources
        val gameCurrencyInPlay = serviceHub.vaultService.queryBy<GameCurrencyState>().states.map { it.state.data }
        val mapOfResources = mapOf(
                gameBoardState.players[0] to gameCurrencyInPlay.filter { it.holder.owningKey == gameBoardState.players[0].owningKey }.sumBy { it.fungibleToken.amount.quantity.toInt() },
                gameBoardState.players[1] to gameCurrencyInPlay.filter { it.holder.owningKey == gameBoardState.players[1].owningKey }.sumBy { it.fungibleToken.amount.quantity.toInt() },
                gameBoardState.players[2] to gameCurrencyInPlay.filter { it.holder.owningKey == gameBoardState.players[2].owningKey }.sumBy { it.fungibleToken.amount.quantity.toInt() },
                gameBoardState.players[3] to gameCurrencyInPlay.filter { it.holder.owningKey == gameBoardState.players[3].owningKey }.sumBy { it.fungibleToken.amount.quantity.toInt() })


        var playBlockerCommand: PlayBlockerContract.Commands.IssuePlayBlockers? = null

        mapOfResources.forEach { (party, int) ->
            if (int > 7) {
                playBlockerCommand = PlayBlockerContract.Commands.IssuePlayBlockers()
                tb.addOutputState(PlayBlockerState(party, gameBoardState.players, int/2, BlockedStatus.MUST_DISCARD_RESOURCES, gameBoardLinearId))
            }
        }

        // Step 8. Add a PlayBlockerState for the target player
        if (robber.targetPlayer != null) {
            tb.addOutputState(PlayBlockerState(robber.targetPlayer!!, gameBoardState.players, 1, BlockedStatus.MUST_PAY_ROBBER_STOLEN_RESOURCE, gameBoardLinearId))
            if (playBlockerCommand != null) playBlockerCommand = PlayBlockerContract.Commands.IssuePlayBlockers()
        }

        // Step 9. Add the PlayBlockerCommand if it exists
        if (playBlockerCommand != null) tb.addCommand(playBlockerCommand!!, gameBoardState.playerKeys())

        // Step 9. Create the appropriate command
        val robberCommand = RobberContract.Commands.ApplyRobber()

        // Step 10. Add all input/output states
        tb.addInputState(robberStateAndRef)
        tb.addOutputState(deactivatedRobberState)
        tb.addReferenceState(gameBoardReferenceStateAndRef)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)
        tb.addCommand(robberCommand, gameBoardState.playerKeys())

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

@InitiatedBy(ApplyRobberFlow::class)
class ApplyRobberFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
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
