package com.r3.cordan.primary.flows.robber

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.oracle.client.contracts.DiceRollContract
import com.r3.cordan.primary.contracts.robber.RobberContract
import com.r3.cordan.primary.flows.queryDiceRoll
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.states.board.GameBoardState
import com.r3.cordan.primary.states.board.HexTileIndex
import com.r3.cordan.primary.states.robber.RobberState
import com.r3.cordan.primary.states.turn.TurnTrackerState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow(version = 1)
@StartableByRPC
class MoveRobberFlow(val gameBoardLinearId: UniqueIdentifier,
                     val updatedRobberLocation: Int,
                     val targetPlayer: Party? = null) : FlowLogic<SignedTransaction>() {
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

        // Step 4. Retrieve the Dice Roll State from the vault
        val diceRollStateAndRef = serviceHub.vaultService
                .queryDiceRoll(gameBoardLinearId)

        // Step 5. Add the existing robber state as an input state
        val robberStateAndRef = serviceHub.vaultService
                .querySingleState<RobberState>(gameBoardState.robberLinearId)
        if (!gameBoardState.isValid(robberStateAndRef.state.data)) {
            throw FlowException("The robber state does not point back to the GameBoardState")
        }

        // Step 6. Create a new robber state
        val movedRobberState = robberStateAndRef.state.data.moveAndActivate(HexTileIndex(updatedRobberLocation), targetPlayer)

        // Step 7. Create the appropriate command
        val robberCommand = RobberContract.Commands.MoveRobber()
        val diceRollCommand = DiceRollContract.Commands.ConsumeDiceRoll()

        // Step 8. Create a transaction builder and add all input/output states
        val tb = TransactionBuilder(notary)
        tb.addInputState(robberStateAndRef)
        tb.addInputState(diceRollStateAndRef)
        tb.addOutputState(movedRobberState)
        tb.addReferenceState(gameBoardReferenceStateAndRef)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)
        tb.addCommand(robberCommand, gameBoardState.playerKeys())
        tb.addCommand(diceRollCommand, gameBoardState.playerKeys())

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

@InitiatedBy(MoveRobberFlow::class)
class MoveRobberFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
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
