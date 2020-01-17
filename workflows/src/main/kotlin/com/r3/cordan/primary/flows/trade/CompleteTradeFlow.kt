package com.r3.cordan.primary.flows.trade

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.states.board.GameBoardState
import com.r3.cordan.primary.states.trade.TradeState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.Vault
import net.corda.core.transactions.SignedTransaction

// **********************
// * Complete Trade Flow *
// **********************

/**
 * This flow allows a counter-party to trigger the execution of a trade posted by
 * another node. It facilitates the exchange of tokens and checks that the balances
 * of the tokens exchanged matches the TradeState original proposed.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class CompleteTradeFlow(private val tradeStateLinearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {

        // Execute the trade
        val stxWithTrade = subFlow(ExecuteTradeFlow(tradeStateLinearId))

        // Get access to the trade state
        val tradeState = serviceHub.vaultService
                .querySingleState<TradeState>(stxWithTrade.inputs, Vault.StateStatus.CONSUMED).state.data

        // Get access to the gameBoardState associated with the trade
        val gameBoardState = serviceHub.vaultService
                .querySingleState<GameBoardState>(stxWithTrade.references).state.data

        // Inform all players included in the game of the trade
        (gameBoardState.players - tradeState.participants)
                .forEach { subFlow(SendTransactionFlow(initiateFlow(it), stxWithTrade)) }

        return stxWithTrade
    }
}

@InitiatingFlow
@InitiatedBy(CompleteTradeFlow::class)
class CompleteTradeFlowResponder(val otherSideSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ReceiveTransactionFlow(otherSideSession, true, StatesToRecord.ALL_VISIBLE))
    }
}