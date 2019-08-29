package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.TradePhaseContract
import com.contractsAndStates.states.*
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import net.corda.core.contracts.FungibleState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * This flow will allow users to exchange their existing resources at a port for other resources. It is
 * facilitated using the redeem and issue token utilities to exchange assets at a verifiable rate.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class TradeWithPortFlow(
        val gameBoardLinearId: UniqueIdentifier,
        tileOfPort: Int,
        tileCornerOfPort: Int,
        val soldResource: Resource,
        val purchasedResource: Resource) : FlowLogic<SignedTransaction>() {

    val hexTileOfPort: HexTileIndex
    val tileCorner: TileCornerIndex

    init {
        hexTileOfPort = HexTileIndex((tileOfPort))
        tileCorner = TileCornerIndex(tileCornerOfPort)
    }

    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardReferenceStateAndRef = ReferencedStateAndRef(gameBoardStateAndRef)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 2. Get reference to the notary and oracle
        val notary = gameBoardStateAndRef.state.notary

        // Step 3. Get access to the port with which the user wishes to trade
        val portToBeTradedWith = gameBoardState.ports.getPortAt(hexTileOfPort, tileCorner).portTile

        // Step 4. Generate an exit for the tokens that will be consumed by the port.
        val tb = TransactionBuilder(notary)
        val sold = portToBeTradedWith.getInputOf(soldResource)
        generateInGameSpend(serviceHub, tb, mapOf(sold.token to sold.quantity), ourIdentity)

        // Step 5. Generate all tokens and commands for issuance from the port
        val playerKeys = gameBoardState.playerKeys()
        val purchased = portToBeTradedWith.getOutputOf(purchasedResource)
        addIssueTokens(tb, arrayListOf(purchased issuedBy ourIdentity heldBy ourIdentity forGameBoard gameBoardLinearId), gameBoardState.playerKeys() - ourIdentity.owningKey )
        tb.addCommand(TradePhaseContract.Commands.TradeWithPort(hexTileOfPort, tileCorner), playerKeys)
        tb.addCommand(IssueTokenCommand(purchased.token issuedBy ourIdentity), playerKeys)

        // Step 6. Add all necessary states to the transaction
        tb.addReferenceState(gameBoardReferenceStateAndRef)

        // Step 7. Verify Transaction
        tb.verify(serviceHub)

        // Step 8. Collect the signatures and sign the transaction
        val ptx = serviceHub.signInitialTransaction(tb)
        val sessions = (gameBoardState.players - ourIdentity).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))
        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(TradeWithPortFlow::class)
open class TradeWithPortFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val listOfTokensIssued = stx.coreTransaction.outputsOfType<FungibleState<*>>().toMutableList()
                val gameBoardStateRef = stx.coreTransaction.references.single()
                val gameBoardState = serviceHub.vaultService
                        .querySingleState<GameBoardState>(gameBoardStateRef)
                        .state.data
                if (listOfTokensIssued.any { (it as GameCurrencyState).gameBoardId != gameBoardState.linearId }) {
                    throw FlowException("Game currency is being generated for a game board that is not referenced. No Cheating pls.")
                }
                val turnTrackerState = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
                        .state.data
                if (!gameBoardState.isValid(turnTrackerState)) {
                    throw FlowException("The turn tracker state does not point back to the GameBoardState")
                }

                if (counterpartySession.counterparty.owningKey != gameBoardState
                                .players[turnTrackerState.currTurnIndex].owningKey) {
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