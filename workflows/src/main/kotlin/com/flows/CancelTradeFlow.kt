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
class CancelTradeFlow(val tradeStateLinearId: UniqueIdentifier): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Step 1. Get reference to the notary and oracle
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Get the GameBoard state from the vault.
        val gameBoardState = serviceHub.vaultService.queryBy<GameBoardState>().states.single().state.data

        // Step 2. Retrieve the Turn Tracker State from the vault.
        val queryCriteriaForTradeState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(tradeStateLinearId))
        val tradeStateAndRef = serviceHub.vaultService.queryBy<TradeState>(queryCriteriaForTradeState).states.single()

        // Step 3. Create a transaction builder
        val tb = TransactionBuilder(notary = notary)

        // Step 4. Add the new trade state to the transaction.
        tb.addInputState(tradeStateAndRef)

        // Step 5. Add the gather resources command and verify the transaction
        val commandSigners = gameBoardState.players.map { it.owningKey }
        tb.addCommand(TradePhaseContract.Commands.CancelTrade(), commandSigners)
        tb.verify(serviceHub)

        // Step 6. Collect the signatures and sign the transaction
        val ptx = serviceHub.signInitialTransaction(tb)
        val sessions = (gameBoardState.players - ourIdentity).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))
        return subFlow(FinalityFlow(stx, sessions))
    }

}

@InitiatedBy(CancelTradeFlow::class)
open class CancelTradeFlowResponder(val counterpartySession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val tradeStateRefs = stx.coreTransaction.inputs
                val tradeState = serviceHub.vaultService.queryBy<TradeState>(QueryCriteria.VaultQueryCriteria(stateRefs = tradeStateRefs)).states.single().state.data

                // Ensure that the player cancelling the trade is the owner of the trade.
                if (counterpartySession.counterparty.owningKey != tradeState.owner.owningKey) {
                    throw IllegalArgumentException("Trades cancelled by the current player must only offer their own assets.")
                }

            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }

}
