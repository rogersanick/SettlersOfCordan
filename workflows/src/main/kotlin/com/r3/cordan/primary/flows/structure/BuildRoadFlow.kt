package com.r3.cordan.primary.flows.structure

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.primary.contracts.structure.BuildPhaseContract
import com.r3.cordan.primary.contracts.board.GameStateContract
import com.r3.cordan.primary.contracts.win.LongestRoadContract
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.service.GenerateSpendService
import com.r3.cordan.primary.states.board.AbsoluteSide
import com.r3.cordan.primary.states.board.GameBoardState
import com.r3.cordan.primary.states.board.HexTileIndex
import com.r3.cordan.primary.states.board.TileSideIndex
import com.r3.cordan.primary.states.structure.*
import com.r3.cordan.primary.states.turn.TurnTrackerState
import com.r3.cordan.primary.states.win.LongestRoadState
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// *******************
// * Build Road Flow *
// *******************

/**
 * This flow allows for the creation of a RoadState and its placement
 * on the game board in a non-conflicting position.
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class BuildRoadFlow(
        val gameBoardLinearId: UniqueIdentifier,
        hexTileIndex: Int,
        hexTileSide: Int
) : FlowLogic<SignedTransaction>() {

    val absoluteSide = AbsoluteSide(HexTileIndex(hexTileIndex), TileSideIndex(hexTileSide))

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
                .queryBy<RoadState>().states
                .map { it.state.data }
        val settlementStates = serviceHub.vaultService
                .queryBy<SettlementState>().states
                .map { it.state.data }
        val longestRoadStateStateAndRef = serviceHub.vaultService
                .queryBy<LongestRoadState>().states.first()
        val longestRoadState = longestRoadStateStateAndRef.state.data

        // Step 4. Retrieve the Turn Tracker State from the vault
        val turnTrackerReferenceStateAndRef = ReferencedStateAndRef(
                serviceHub.vaultService.querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId))
        if (!gameBoardState.isValid(turnTrackerReferenceStateAndRef.stateAndRef.state.data)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }

        // Step 5. Create a new transaction builder
        val tb = TransactionBuilder(notary)

        // Step 6. Create new commands for placing a settlement.
        val buildRoadCommand = Command(
                BuildPhaseContract.Commands.BuildRoad(),
                gameBoardState.playerKeys())
        tb.addCommand(buildRoadCommand)

        // Step 7. Create initial road state
        val roadState = RoadState(
                gameBoardLinearId = gameBoardState.linearId,
                absoluteSide = absoluteSide,
                players = gameBoardState.players,
                owner = ourIdentity)

        // Step 8. Determine if the road state is extending an existing road
        val newBoardStateBuilder = gameBoardState.toBuilder()
        newBoardStateBuilder.setRoadOn(absoluteSide, roadState.linearId)
        val outputGameBoardState = newBoardStateBuilder.build()

        // Step 9. Determine new longest road holder
        val longestRoadHolder = longestRoad(
                board = gameBoardState.hexTiles,
                roads = roadStates,
                settlements = settlementStates,
                players = gameBoardState.players,
                currentHolder = longestRoadState.holder)
        val outputLongestRoadState = longestRoadState.copy(holder = longestRoadHolder)

        // Add resources to pay off the play blocker state
        serviceHub.cordaService(GenerateSpendService::class.java)
                .generateInGameSpend(gameBoardState.linearId, tb, getBuildableCosts(Buildable.Road), ourIdentity, ourIdentity)

        // Step 10. Add all states and commands to the transaction.
        tb.addInputState(gameBoardStateAndRef)
        tb.addReferenceState(turnTrackerReferenceStateAndRef)
        tb.addOutputState(roadState, BuildPhaseContract.ID)
        tb.addOutputState(outputGameBoardState, GameStateContract.ID)
        if (outputLongestRoadState.holder != longestRoadState.holder) {
            tb.addInputState(longestRoadStateStateAndRef)
            tb.addOutputState(outputLongestRoadState, LongestRoadContract.ID)
        }

        // Step 11. Sign initial transaction

        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 12. Collect all signatures
        val sessions = (gameBoardState.players - ourIdentity).map { initiateFlow(it) }.toSet()
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(BuildRoadFlow::class)
class BuildRoadFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val gameBoardState = stx.coreTransaction.outputsOfType<GameBoardState>().first()
                val turnTrackerStateRef = stx.coreTransaction.references.single()
                val turnTrackerState = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(turnTrackerStateRef).state.data

                val lastTurnTrackerOnRecordStateAndRef = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(turnTrackerState.linearId).state.data
                if (lastTurnTrackerOnRecordStateAndRef.linearId != turnTrackerState.linearId) {
                    throw FlowException("The TurnTracker included in the transaction is not correct for this game or turn.")
                }
                if (!gameBoardState.isValid(lastTurnTrackerOnRecordStateAndRef)) {
                    throw FlowException("The turn tracker state does not point back to the GameBoardState")
                }

                if (counterpartySession.counterparty.owningKey != gameBoardState.players[lastTurnTrackerOnRecordStateAndRef.currTurnIndex].owningKey) {
                    throw IllegalArgumentException("Only the current player may build a road.")
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