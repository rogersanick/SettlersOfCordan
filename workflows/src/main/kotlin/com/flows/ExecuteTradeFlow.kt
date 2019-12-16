package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.GameCurrencyContract
import com.contractsAndStates.states.*
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.utilities.addTokenTypeJar
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.excludeHostNode
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.finance.flows.TwoPartyDealFlow
import java.security.PublicKey
import java.util.stream.Collectors

// **********************
// * Execute Trade Flow *
// **********************

/**
 * This flow allows a counter-party to trigger the execution of a trade posted by
 * another node. It facilitates the exchange of tokens and checks that the balances
 * of the tokens exchanged matches the TradeState original proposed.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class ExecuteTradeFlow(private val tradeStateLinearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

    companion object {
        object RECEIVED : ProgressTracker.Step("Received API call")
        object DEALING : ProgressTracker.Step("Starting the deal flow") {
            override fun childProgressTracker(): ProgressTracker = TwoPartyDealFlow.Primary.tracker()
        }

        // We vend a progress tracker that already knows there's going to be a TwoPartyTradingFlow involved at some
        // point: by setting up the tracker in advance, the user can see what's coming in more detail, instead of being
        // surprised when it appears as a new set of tasks below the current one.
        fun tracker() = ProgressTracker(RECEIVED, DEALING)
    }

    override val progressTracker = tracker()

    init {
        progressTracker.currentStep = RECEIVED
    }

    @Suspendable
    override fun call(): SignedTransaction {

        // 1. Retrieve the appropriate trade state from the linearID.
        val tradeStateAndRef = serviceHub.vaultService
                .querySingleState<TradeState>(tradeStateLinearId)
                .state
        val tradeState = tradeStateAndRef.data

        // 2. Use the tokenSDK to generate the movement of resources required to execute the trade.
        val tb = TransactionBuilder()
        val tokenSelection = TokenSelection(serviceHub)
        val (inputGameCurrency, outputGameCurrency) = tokenSelection.generateMoveGameCurrency<GameCurrencyState>(
                tb.lockId,
                listOf(PartyAndAmount(tradeState.owner, tradeState.wanted)),
                ourIdentity,
                tradeState.gameBoardLinearId
        )
        addMoveTokens(tb, inputGameCurrency, outputGameCurrency)

        // 3. Get a reference to the notary and assign it to the transaction.
        require(serviceHub.networkMapCache.notaryIdentities.isNotEmpty()) { "No notary nodes registered" }
        val notary = tradeStateAndRef.notary

        // 4. Get a reference to the counter-party of the trade.
        val otherParty = excludeHostNode(serviceHub, groupAbstractPartyByWellKnownParty(serviceHub, listOf(tradeState.owner))).keys.single()
        progressTracker.currentStep = DEALING

        // 5. Use the TwoPartyDealFlow instigation flow to kick off the execution of the trade.
        // Note: We propose the movement of our tokens in this trade to facilitate DvP
        val session = initiateFlow(otherParty)
        val instigator = TwoPartyDealFlow.Instigator(
                session,
                TwoPartyDealFlow.AutoOffer(
                        notary,
                        tradeState.copy(informationForAcceptor = InformationForAcceptor(
                                tb.inputStates().map { serviceHub.vaultService.querySingleState<GameCurrencyState>(it) },
                                tb.outputStates(),
                                tb.commands(),
                                tb.attachments(),
                                tradeState.gameBoardLinearId,
                                tradeStateLinearId
                        ))),
                progressTracker.getChildProgressTracker(DEALING)!!
        )
        return subFlow(instigator)
    }
}

@InitiatingFlow
@InitiatedBy(ExecuteTradeFlow::class)
class ExecuteTradeFlowResponder(otherSideSession: FlowSession) : TwoPartyDealFlow.Acceptor(otherSideSession) {

    @Suspendable
    override fun assembleSharedTX(handshake: TwoPartyDealFlow.Handshake<TwoPartyDealFlow.AutoOffer>): Triple<TransactionBuilder, List<PublicKey>, List<TransactionSignature>> {

        // Retrieve the payload from the handshake
        val handShakeToAssembleSharedTX = handshake.payload.dealBeingOffered as ExtendedDealState
        val gameBoardLinearId = handShakeToAssembleSharedTX.gameBoardLinearId

        // Retrieve the counterParty input states, outputs states and other relevant data
        val counterPartyGameCurrencyInputs = handShakeToAssembleSharedTX.informationForAcceptor!!.inputStates.stream().collect(Collectors.toList())
        val counterPartyGameCurrencyOutputs = handShakeToAssembleSharedTX.informationForAcceptor!!.outputStates.stream().collect(Collectors.toList()).map { it.data as GameCurrencyState }
        val counterPartyAttachments = handShakeToAssembleSharedTX.informationForAcceptor!!.attachments.stream().collect(Collectors.toList())
        val counterPartyCommands = handShakeToAssembleSharedTX.informationForAcceptor!!.commands.stream().collect(Collectors.toList())
        val counterPartyGameBoardLinearId = handShakeToAssembleSharedTX.informationForAcceptor!!.gameBoardLinearId
        val counterPartyTradeStateLinearId = handShakeToAssembleSharedTX.informationForAcceptor!!.tradeStateLinearId

        // query our vault to get the appropriate states to reference
        val tradeState = serviceHub.vaultService.querySingleState<TradeState>(counterPartyTradeStateLinearId)
        val gameBoardState = serviceHub.vaultService.querySingleState<GameBoardState>(counterPartyGameBoardLinearId)
        val turnTrackerState = serviceHub.vaultService.querySingleState<TurnTrackerState>(gameBoardState.state.data.turnTrackerLinearId)

        // Use the counter-party input states and commands when initialising a new transaction builder
        val tb = TransactionBuilder(
                notary = handshake.payload.notary,
                commands = counterPartyCommands,
                attachments = counterPartyAttachments
        )

        counterPartyGameCurrencyInputs.forEach { tb.addInputState(it) }
        counterPartyGameCurrencyOutputs.forEach { tb.addOutputState(it, GameCurrencyContract.contractId) }

        val tokenSelection = TokenSelection(serviceHub)
        val (inputGameCurrency, outputGameCurrency) = tokenSelection.generateMoveGameCurrency<GameCurrencyState>(
                tb.lockId,
                listOf(PartyAndAmount(otherSideSession.counterparty, handShakeToAssembleSharedTX.offering)),
                ourIdentity,
                gameBoardLinearId)
        addMoveTokens(tb, inputGameCurrency, outputGameCurrency)
        addTokenTypeJar(tb.outputStates().filterIsInstance<TransactionState<GameCurrencyState>>().map { it.data }, tb)

        // Add the trade state to the transaction.
        tb.addInputState(tradeState)
        tb.addReferenceState(ReferencedStateAndRef(turnTrackerState))
        tb.addReferenceState(ReferencedStateAndRef(gameBoardState))

        // Use the generateAgreement method on the ExtendedDealState class to add the appropriate commands to the transaction.
        val ptx = handShakeToAssembleSharedTX.generateAgreement(tb)

        // We set the transaction's time-window: it may be that none of the contracts need this!
        // But it can't hurt to have one.
        ptx.setTimeWindow(serviceHub.clock.instant(), 60.seconds)
        return Triple(ptx, arrayListOf(handShakeToAssembleSharedTX.participants.single { it is Party && serviceHub.myInfo.isLegalIdentity(it) }.owningKey), emptyList())
    }

}

@InitiatedBy(ExecuteTradeFlowResponder::class)
class ExecuteTradeFlowResponderNonParticipant(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val transactionReferences = stx.coreTransaction.references

                val turnTrackerReferencedInTransaction = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(transactionReferences)
                        .state.data

                val gameBoard = serviceHub.vaultService
                        .querySingleState<GameBoardState>(transactionReferences)
                        .state.data

                val lastTurnTrackerWeHaveOnRecord = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(gameBoard.turnTrackerLinearId)
                        .state.data

                val tradeStateWeHaveOnRecord = serviceHub.vaultService.querySingleState<TradeState>(stx.inputs).state.data

                if (lastTurnTrackerWeHaveOnRecord.linearId != turnTrackerReferencedInTransaction.linearId) {
                    throw IllegalArgumentException("The TurnTracker included in the transaction is not correct for this game or turn.")
                }

                if (!gameBoard.isValid(lastTurnTrackerWeHaveOnRecord)) {
                    throw FlowException("The turn tracker state does not point back to the GameBoardState")
                }

                if (
                        tradeStateWeHaveOnRecord.targetPlayer != gameBoard.players[turnTrackerReferencedInTransaction.currTurnIndex]
                        && tradeStateWeHaveOnRecord.owner != gameBoard.players[turnTrackerReferencedInTransaction.currTurnIndex]
                ) {
                    throw IllegalArgumentException("The player who is executing the trade is not currently the player whose turn it is.")
                }
            }
        }

        return subFlow(signedTransactionFlow)
    }
}


