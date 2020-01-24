package com.r3.cordan.primary.flows.robber

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.primary.contracts.robber.PlayBlockerContract
import com.r3.cordan.primary.contracts.robber.RobberContract
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.states.resources.GameCurrencyState
import com.r3.cordan.primary.states.robber.BlockedStatus
import com.r3.cordan.primary.states.robber.PlayBlockerState
import com.r3.cordan.primary.states.robber.RobberState
import com.r3.cordan.primary.states.board.GameBoardState
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
class ApplyRobberFlow(val gameBoardLinearId: UniqueIdentifier, val activatedByKnight: Boolean = false) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        // Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data
        val gameBoardReferenceStateAndRef = ReferencedStateAndRef(gameBoardStateAndRef)

        // Get a reference to the notary service on the network
        val notary = gameBoardStateAndRef.state.notary

        // Retrieve the Turn Tracker State from the vault
        val turnTrackerStateAndRef = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
        if (!gameBoardState.isValid(turnTrackerStateAndRef.state.data)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(turnTrackerStateAndRef)

        // Add the existing robber state as an input state
        val robberStateAndRef = serviceHub.vaultService
                .querySingleState<RobberState>(gameBoardState.robberLinearId)
        val robber = robberStateAndRef.state.data
        if (!gameBoardState.isValid(robberStateAndRef.state.data)) {
            throw FlowException("The robber state does not point back to the GameBoardState")
        }
        if (!robber.active) {
            throw FlowException("The robber state is not active")
        }

        // Create a new robber state
        val deactivatedRobberState = robberStateAndRef.state.data.deactivate()

        // Create a transaction builder
        val tb = TransactionBuilder(notary)

        // Add PlayBlockerStates for each of the players with more than 7 resources
        if (!activatedByKnight) {
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
                    tb.addOutputState(PlayBlockerState(party, gameBoardState.players, int / 2, BlockedStatus.MUST_DISCARD_RESOURCES, gameBoardLinearId))
                }
            }

            // Add a PlayBlockerState for the target player
            if (robber.targetPlayer != null) {
                tb.addOutputState(PlayBlockerState(robber.targetPlayer!!, gameBoardState.players, 1, BlockedStatus.MUST_PAY_ROBBER_STOLEN_RESOURCE, gameBoardLinearId))
                if (playBlockerCommand != null) playBlockerCommand = PlayBlockerContract.Commands.IssuePlayBlockers()
            }

            // Add the PlayBlockerCommand if it exists
            if (playBlockerCommand != null) tb.addCommand(playBlockerCommand!!, gameBoardState.playerKeys())

        } else {

            // Add a PlayBlockerState for the target player
            if (robber.targetPlayer != null) {
                tb.addOutputState(PlayBlockerState(robber.targetPlayer!!, gameBoardState.players, 1, BlockedStatus.MUST_PAY_ROBBER_STOLEN_RESOURCE, gameBoardLinearId))
                tb.addCommand(PlayBlockerContract.Commands.IssuePlayBlockers(), gameBoardState.playerKeys())
            }

        }

        // Create the appropriate command
        val robberCommand = RobberContract.Commands.ApplyRobber(activatedByKnight)

        // Add all input/output states
        tb.addInputState(robberStateAndRef)
        tb.addOutputState(deactivatedRobberState)
        tb.addReferenceState(gameBoardReferenceStateAndRef)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)
        tb.addCommand(robberCommand, gameBoardState.playerKeys())

        // Verify and sign the transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Collect Signatures on the transaction
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // Finalize the transaction
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
