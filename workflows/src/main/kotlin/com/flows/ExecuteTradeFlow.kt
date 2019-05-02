package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.TradePhaseContract
import com.contractsAndStates.states.*
import com.r3.corda.sdk.token.workflow.selection.TokenSelection
import net.corda.core.contracts.*
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.finance.flows.TwoPartyDealFlow
import java.security.PublicKey
import java.util.stream.Collectors

// *************************************
// * Initial Settlement Placement Flow *
// *************************************

@InitiatingFlow
@StartableByRPC
class ExecuteTradeFlow(private val tradeStateLinearId: UniqueIdentifier): FlowLogic<SignedTransaction>() {

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

        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(tradeStateLinearId))
        val tradeStateOnWhichToMakeAnOffer = serviceHub.vaultService.queryBy<TradeState>(queryCriteria).states.single().state.data

        val tokenSelection = TokenSelection(serviceHub)

        val tbWithOfferedTokensMovedFromUsToThem = tokenSelection.generateMove(
                TransactionBuilder(),
                tradeStateOnWhichToMakeAnOffer.wanted,
                tradeStateOnWhichToMakeAnOffer.owner
        )

        require(serviceHub.networkMapCache.notaryIdentities.isNotEmpty()) { "No notary nodes registered" }
        val notary = serviceHub.networkMapCache.notaryIdentities.first() // TODO We should pass the notary as a parameter to the flow, not leave it to random choice.

        // need to pick which ever party is not us
        val otherParty = excludeHostNode(serviceHub, groupAbstractPartyByWellKnownParty(serviceHub, listOf(tradeStateOnWhichToMakeAnOffer.owner))).keys.single()
        progressTracker.currentStep = DEALING
        val session = initiateFlow(otherParty)
        val instigator = TwoPartyDealFlow.Instigator(
                session,
                TwoPartyDealFlow.AutoOffer(
                        notary,
                        tradeStateOnWhichToMakeAnOffer.copy(informationForAcceptor = InformationForAcceptor(
                        tbWithOfferedTokensMovedFromUsToThem.first.inputStates(),
                        tbWithOfferedTokensMovedFromUsToThem.first.outputStates(),
                        tbWithOfferedTokensMovedFromUsToThem.first.commands()
                ))),
                progressTracker.getChildProgressTracker(DEALING)!!
        )
        return subFlow(instigator)
    }
}

@InitiatedBy(ExecuteTradeFlow::class)
class ExecuteTradeFlowResponder(otherSideSession: FlowSession): TwoPartyDealFlow.Acceptor(otherSideSession) {

    @Suspendable
    override fun assembleSharedTX(handshake: TwoPartyDealFlow.Handshake<TwoPartyDealFlow.AutoOffer>): Triple<TransactionBuilder, List<PublicKey>, List<TransactionSignature>> {

        val handShakeToAssembleSharedTX = handshake.payload.dealBeingOffered as ExtendedDealState

        val ourIdentity = serviceHub.myInfo.legalIdentities.single()
        val tokenSelection = TokenSelection(serviceHub)

        val newInputStates = handShakeToAssembleSharedTX.informationForAcceptor!!.inputStates.stream().collect(Collectors.toList())
        val newOutputStates = handShakeToAssembleSharedTX.informationForAcceptor!!.outputStates.stream().collect(Collectors.toList())
        val newCommands = handShakeToAssembleSharedTX.informationForAcceptor!!.commands.stream().collect(Collectors.toList())

        val tbWithWantedMoved = tokenSelection.generateMove(
                builder = TransactionBuilder(
                        inputs = newInputStates,
                        outputs = newOutputStates,
                        commands = newCommands
                ),
                amount = handShakeToAssembleSharedTX.offering,
                recipient = otherSideSession.counterparty
        )

        tbWithWantedMoved.first.addCommand(
                TradePhaseContract.Commands.ExecuteTrade(),
                listOf(ourIdentity.owningKey, handShakeToAssembleSharedTX.owner.owningKey)
        )

        val ptx = handShakeToAssembleSharedTX.generateAgreement(tbWithWantedMoved.first)

        // We set the transaction's time-window: it may be that none of the contracts need this!
        // But it can't hurt to have one.
        ptx.setTimeWindow(serviceHub.clock.instant(), 60.seconds)
        return Triple(ptx, arrayListOf(handShakeToAssembleSharedTX.participants.single { it is Party && serviceHub.myInfo.isLegalIdentity(it) }.owningKey), emptyList())
    }

}

