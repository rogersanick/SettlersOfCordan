package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.BuildPhaseContract
import com.contractsAndStates.contracts.GameStateContract
import com.contractsAndStates.contracts.LongestRoadContract
import com.contractsAndStates.states.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder


// *************************
// * Build Settlement Flow *
// *************************

/**
 * This is the flow nodes may execute to consume resources and issue a new settlement
 * state onto the ledger at a location of their choosing. New settlements will be used
 * in the future to claim additional resources in the GatherResourcesFlow.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class BuildSettlementFlow(
        val gameBoardLinearId: UniqueIdentifier,
        tileIndex: Int,
        cornerIndex: Int
) : FlowLogic<SignedTransaction>() {

    val absoluteCorner = AbsoluteCorner(HexTileIndex(tileIndex), TileCornerIndex(cornerIndex))

    @Suspendable
    override fun call(): SignedTransaction {

        // Step 1. Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 2. Get a reference to the notary service on the network
        val notary = gameBoardStateAndRef.state.notary

        // Step 3. Retrieve roads, settlements and current longest road state
        val roadStates = serviceHub.vaultService
                .queryBy<RoadState>().states.map { it.state.data }
        val settlementStates = serviceHub.vaultService
                .queryBy<SettlementState>().states.map { it.state.data }
        val longestRoadStateStateAndRef = serviceHub.vaultService
                .queryBy<LongestRoadState>().states.first()
        val longestRoadState = longestRoadStateStateAndRef.state.data

        // Step 4. Retrieve the Turn Tracker State from the vault
        val turnTrackerStateAndRef = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
        if (!gameBoardState.isValid(turnTrackerStateAndRef.state.data)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(turnTrackerStateAndRef)

        // Step 5. Create a new transaction builder
        val tb = TransactionBuilder(notary)

        // Step 6. Create new commands for placing a settlement and ending a turn. Add both to the transaction.
        val buildSettlement = Command(BuildPhaseContract.Commands.BuildSettlement(), gameBoardState.playerKeys())
        tb.addCommand(buildSettlement)

        // Step 7. Create initial settlement
        val settlementState = SettlementState(
                gameBoardLinearId = gameBoardState.linearId,
                absoluteCorner = absoluteCorner,
                players = gameBoardState.players,
                owner = ourIdentity)

        // Step 8. Prepare a new Game Board State
        val newBoardBuilder = gameBoardState.toBuilder()

        // Step 9. Safely put the settlement on all overlapping corners.
        newBoardBuilder.setSettlementOn(absoluteCorner, settlementState.linearId)

        // Step 10. Add the appropriate resources to the transaction to pay for the Settlement.
        serviceHub.cordaService(GenerateSpendService::class.java)
                .generateInGameSpend(serviceHub, tb, getBuildableCosts(Buildable.Settlement), ourIdentity, ourIdentity)

        // Step 11. Determine new longest road holder
        val longestRoadHolder = longestRoad(
                board = gameBoardState.hexTiles,
                roads = roadStates,
                settlements = settlementStates,
                players = gameBoardState.players,
                currentHolder = longestRoadState.holder)
        val outputLongestRoadState = longestRoadState.copy(holder = longestRoadHolder)

        // Step 12. Add all states and commands to the transaction.
        tb.addInputState(gameBoardStateAndRef)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)
        tb.addOutputState(settlementState, BuildPhaseContract.ID)
        tb.addOutputState(newBoardBuilder.build())
        tb.addCommand(GameStateContract.Commands.UpdateWithSettlement(), gameBoardState.playerKeys())
        if (outputLongestRoadState.holder != longestRoadState.holder) {
            tb.addInputState(longestRoadStateStateAndRef)
            tb.addOutputState(outputLongestRoadState, LongestRoadContract.ID)
        }

        serviceHub.networkMapCache.notaryIdentities.first()
        serviceHub.networkMapCache.notaryIdentities.first()
        // Step 11. Sign initial transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 12. Collect all signatures
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(BuildSettlementFlow::class)
class BuildSettlementFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val gameBoardState = stx.coreTransaction.outputsOfType<GameBoardState>().first()
                val turnTrackerStateRef = stx.coreTransaction.references.single()
                val turnTrackerState = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(turnTrackerStateRef)
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
