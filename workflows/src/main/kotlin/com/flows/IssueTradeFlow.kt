package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.TradePhaseContract
import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.TradeState
import com.contractsAndStates.states.TurnTrackerState
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// ********************
// * Issue Trade Flow *
// ********************

/**
 * This flow enables players to post and advertise trades that they would like to make
 * with specific counter-parties in the game. These trades can then be executed at anytime
 * (given they haven't been cancelled) by the target counter-party.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class IssueTradeFlow(
        val amountOffered: Amount<TokenType>,
        val amountWanted: Amount<TokenType>,
        val targetPlayer: Party,
        val gameBoardLinearId: UniqueIdentifier
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 2. Get reference to the notary and oracle
        val notary = gameBoardStateAndRef.state.notary

        // Step 3. Create a transaction builder
        val tb = TransactionBuilder(notary = notary)

        // Step 4. Generate a trade state.
        val tradeState = TradeState(
                amountOffered,
                amountWanted,
                ourIdentity,
                targetPlayer,
                gameBoardState.players,
                false,
                gameBoardLinearId
        )

        // Step 5. Add the new trade state to the transaction.
        tb.addOutputState(tradeState)

        // Step 6. Add the gather resources command and verify the transaction
        val commandSigners = gameBoardState.players.map { it.owningKey }
        tb.addCommand(TradePhaseContract.Commands.IssueTrade(), commandSigners)
        tb.verify(serviceHub)

        // Step 7. Collect the signatures and sign the transaction
        val ptx = serviceHub.signInitialTransaction(tb)
        val sessions = (gameBoardState.players - ourIdentity).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))
        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(IssueTradeFlow::class)
open class IssueTradeFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val tradeState = stx.coreTransaction.outputsOfType<TradeState>().single()
                val gameBoardState = serviceHub.vaultService
                        .querySingleState<GameBoardState>(tradeState.gameBoardStateLinearId)
                        .state.data
                val lastTurnTrackerOnRecord = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
                        .state.data
                if (!gameBoardState.isValid(lastTurnTrackerOnRecord)) {
                    throw FlowException("The turn tracker state does not point back to the GameBoardState")
                }

                // Ensure that the player proposing the trade is the owner of the trade.
                if (counterpartySession.counterparty.owningKey != tradeState.owner.owningKey) {
                    throw FlowException("Trades proposed by the current player must only offer their own assets.")
                }

                // Ensure that the player proposing the trade is the player with the current turn.
                if (counterpartySession.counterparty.owningKey != gameBoardState.players[lastTurnTrackerOnRecord.currTurnIndex].owningKey) {
                    throw FlowException("Only the player with the current turn can propose a new trade")
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}
