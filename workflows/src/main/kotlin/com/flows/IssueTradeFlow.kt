package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.GatherPhaseContract
import com.contractsAndStates.contracts.TradePhaseContract
import com.contractsAndStates.states.*
import com.oracleClient.contracts.DiceRollContract
import com.oracleClient.state.DiceRollState
import com.r3.corda.sdk.token.contracts.FungibleTokenContract
import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.lang.IllegalArgumentException

// *************************************
// * Initial Settlement Placement Flow *
// *************************************

@InitiatingFlow
@StartableByRPC
class IssueTradeFlow(val amountOffered: Amount<TokenType>, val amountWanted: Amount<TokenType>, val targetPlayer: Party, val gameBoardLinearId: UniqueIdentifier): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Step 1. Get reference to the notary and oracle
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Retrieve the Game Board State from the vault.
        val queryCriteriaForGameBoardState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardLinearId))
        val gameBoardStateAndRef = serviceHub.vaultService.queryBy<GameBoardState>(queryCriteriaForGameBoardState).states.single()
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 3. Retrieve the Turn Tracker State from the vault
        val queryCriteriaForTurnTrackerState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardState.turnTrackerLinearId))
        val turnTrackerStateAndRef = serviceHub.vaultService.queryBy<TurnTrackerState>(queryCriteriaForTurnTrackerState).states.single()

        // Step 4. Create a transaction builder
        val tb = TransactionBuilder(notary = notary)

        // Step 5. Generate a trade state.
        val tradeState = TradeState(
                amountOffered,
                amountWanted,
                ourIdentity,
                targetPlayer,
                gameBoardState.players,
                false,
                gameBoardLinearId
        )

        // Step 6. Add the new trade state to the transaction.
        tb.addOutputState(tradeState)

        // Step 7. Add the gather resources command and verify the transaction
        val commandSigners = gameBoardState.players.map {it.owningKey}
        tb.addCommand(TradePhaseContract.Commands.IssueTrade(), commandSigners)
        tb.verify(serviceHub)

        // Step 10. Collect the signatures and sign the transaction
        val ptx = serviceHub.signInitialTransaction(tb)
        val sessions = (gameBoardState.players - ourIdentity).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))
        return subFlow(FinalityFlow(stx, sessions))
    }

}

@InitiatedBy(IssueTradeFlow::class)
open class IssueTradeFlowResponder(val counterpartySession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val tradeState = stx.coreTransaction.outputsOfType<TradeState>().single()
                val gameBoardState = serviceHub.vaultService.queryBy<GameBoardState>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(tradeState.gameBoardStateLinearId))).states.single().state.data
                val lastTurnTrackerOnRecord = serviceHub.vaultService.queryBy<TurnTrackerState>().states.single().state.data

                // Ensure that the player proposing the trade is the owner of the trade.
                if (counterpartySession.counterparty.owningKey != tradeState.owner.owningKey) {
                    throw IllegalArgumentException("Trades proposed by the current player must only offer their own assets.")
                }

                // Ensure that the player proposing the trade is the player with the current turn.
                if (counterpartySession.counterparty.owningKey != gameBoardState.players[lastTurnTrackerOnRecord.currTurnIndex].owningKey) {
                    throw IllegalArgumentException("Only the player with the current turn can propose a new trade")
                }

            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }

}
