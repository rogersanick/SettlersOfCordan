package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.BuildPhaseContract
import com.contractsAndStates.states.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// *************************
// * Build Settlement Flow *
// *************************

/**
 * This is the flow nodes may execute to consume resources (in addition to an existing settlement and
 * issue a new city state onto the ledger. Cities will be used in the future to claim additional
 * resources in the GatherResourcesFlow.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class BuildCityFlow(
        val gameBoardLinearId: UniqueIdentifier,
        val settlementLinearId: UniqueIdentifier
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 2. Get a reference to the notary service on the network
        val notary = gameBoardStateAndRef.state.notary

        // Step 3. Retrieve the Turn Tracker State from the vault
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(
                serviceHub.vaultService.querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId))
        if (gameBoardState.linearId != turnTrackerReferenceStateAndRef.stateAndRef.state.data.gameBoardLinearId) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }

        // Step 4. Create a new transaction builder
        val tb = TransactionBuilder(notary)

        // Step 5. Create new commands for placing a settlement and ending a turn. Add both to the transaction.
        val buildCity = Command(
                BuildPhaseContract.Commands.BuildCity(),
                gameBoardState.players.map { it.owningKey })
        tb.addCommand(buildCity)

        // Step 6. Create city settlement
        val inputSettlementStateAndRef = serviceHub.vaultService
                .querySingleState<SettlementState>(settlementLinearId)
        val outputCityState = inputSettlementStateAndRef.state.data.upgradeToCity()

        // Step 7. Add the appropriate resources to the transaction to pay for the City.
        generateInGameSpend(serviceHub, tb, getBuildableCosts(Buildable.City), ourIdentity)

        // Step 8. Add all states and commands to the transaction.
        tb.addInputState(inputSettlementStateAndRef)
        tb.addOutputState(outputCityState, BuildPhaseContract.ID)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)

        // Step 9. Sign initial transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 10. Collect all signatures
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // Step 11. Run the FinalityFlow
        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(BuildCityFlow::class)
class BuildCityFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val gameBoardStates = stx.coreTransaction.outputsOfType<GameBoardState>()
                require(gameBoardStates.size == 1) { "There should be a single output of GameBoardState" }
                val gameBoardState = gameBoardStates.single()

                val references = stx.coreTransaction.references
                require(references.size == 1) { "There should be a single reference state" }

                val turnTrackerStateRef = references.single()
                val turnTrackerState = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(turnTrackerStateRef)
                        .state.data
                if (gameBoardState.turnTrackerLinearId != turnTrackerState.linearId) {
                    throw FlowException("The TurnTracker included in the transaction is not correct for this game or turn.")
                }
                if (!gameBoardState.isValid(turnTrackerState)) {
                    throw FlowException("The turn tracker state does not point back to the GameBoardState")
                }

                require(counterpartySession.counterparty.owningKey == gameBoardState
                        .players[turnTrackerState.currTurnIndex]
                        .owningKey) {
                    "Only the current player may propose the next move"
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}
