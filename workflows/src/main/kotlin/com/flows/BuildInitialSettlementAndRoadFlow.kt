package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.BuildPhaseContract
import com.contractsAndStates.contracts.GameStateContract
import com.contractsAndStates.states.*
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.issue.addIssueTokens
import com.r3.corda.lib.tokens.workflows.utilities.addTokenTypeJar
import net.corda.core.contracts.Command
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.internal.toMultiMap
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// ******************************************
// * Build Initial Settlement And Road Flow *
// ******************************************

/**
 * After setting up the game, this is the first flow that players will run. It allows them
 * to place settlements and roads to establish an initial position on the game board. It also
 * facilitates the claiming of initial resources that a player is entitled to when executing
 * the second round of setup (e.g - their second execution of this flow).
 */

@InitiatingFlow(version = 1)
@StartableByRPC
class BuildInitialSettlementAndRoadFlow(
        val gameBoardLinearId: UniqueIdentifier,
        tileIndex: Int,
        cornerIndex: Int,
        sideIndex: Int
) : FlowLogic<SignedTransaction>() {

    val absoluteCorner = AbsoluteCorner(HexTileIndex(tileIndex), TileCornerIndex(cornerIndex))
    val absoluteSide = AbsoluteSide(HexTileIndex(tileIndex), TileSideIndex(sideIndex))

    init {
        require(absoluteCorner.cornerIndex.getAdjacentSides().contains(absoluteSide.sideIndex)) {
            "You must build a road next to a settlement"
        }
    }

    @Suspendable
    override fun call(): SignedTransaction {

        /**
         * The following section defines all of the steps required to execute the flow. These steps will
         * be executed in sequence to build an initial settlement.
         */

        // Step 1. Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 2. Get a reference to the notary service on the network
        val notary = gameBoardStateAndRef.state.notary

        // Step 3. Retrieve the Turn Tracker State from the vault
        val turnTrackerStateAndRef = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
        val turnTrackerState = turnTrackerStateAndRef.state.data
        if (!gameBoardState.isValid(turnTrackerState)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }

        // Step 4. Create a new transaction builder
        val tb = TransactionBuilder(notary)

        // Step 5. Create new commands for placing a settlement. Add both to the transaction.
        val placeInitialSettlement = Command(
                BuildPhaseContract.Commands.BuildInitialSettlementAndRoad(),
                gameBoardState.players.map { it.owningKey })
        tb.addCommand(placeInitialSettlement)

        // Step 6. Create an initial settlement state
        val settlementState = SettlementState(
                gameBoardLinearId = gameBoardState.linearId,
                absoluteCorner = absoluteCorner,
                players = gameBoardState.players,
                owner = ourIdentity)

        // Step 7. Prepare a new Game Board State which will contain an updated mapping of where settlements
        // have been placed.
        val boardBuilder = gameBoardState.toBuilder()

        // Step 8. Use the build to place the settlement on overlapping corners.
        boardBuilder.setSettlementOn(absoluteCorner, settlementState.linearId)

        // Step 9. Add self-issued resources if we are in the second round of initial placement
        if (turnTrackerState.setUpRound1Complete) {

            // Calculate the currencies that should be claimed by the player proposing a move.
            val overlappedCorners = gameBoardState
                    .getOverlappedCorners(absoluteCorner)
                    .plus(absoluteCorner)
                    .filterNotNull()
            val issuedTokens = overlappedCorners
                    .map { gameBoardState.get(it.tileIndex) }
                    .filter { it.resourceType != HexTileType.Desert }
                    .map { it.resourceType.resourceYielded!! to settlementState.resourceAmountClaim.toLong() }
                    .toMultiMap()
                    .mapValues { it.value.sum() }
                    .map { it.value of it.key issuedBy ourIdentity heldBy ourIdentity forGameBoard gameBoardLinearId }

            addIssueTokens(tb, issuedTokens)
            addTokenTypeJar(issuedTokens, tb)
        }

        // Step 10. Create the road state at the appropriate location specified by the user.
        val roadState = RoadState(
                gameBoardLinearId = gameBoardState.linearId,
                absoluteSide = absoluteSide,
                players = gameBoardState.players,
                owner = ourIdentity)

        // Step 11. Update the gameBoardState hexTiles with the roads being built.
        boardBuilder.setRoadOn(absoluteSide, roadState.linearId)

        // Step 12. Update the gameBoardState with new hexTiles and built settlements.
        val updatedOutputGameBoardState = boardBuilder.build()

        // Step 13. Add all states and commands to the transaction.
        tb.addInputState(gameBoardStateAndRef)
        tb.addReferenceState(ReferencedStateAndRef(turnTrackerStateAndRef))
        tb.addOutputState(settlementState, BuildPhaseContract.ID)
        tb.addOutputState(roadState, BuildPhaseContract.ID)
        tb.addOutputState(updatedOutputGameBoardState)
        tb.addCommand(GameStateContract.Commands.UpdateWithSettlement(), gameBoardState.players.map { it.owningKey })

        // Step 14. Sign initial transaction
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 15. Collect all signatures
        val sessions = (gameBoardState.players - ourIdentity).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // Step 16. Run the FinalityFlow to persist the transaction to the ledgers of all appropriate parties.
        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(BuildInitialSettlementAndRoadFlow::class)
class BuildInitialSettlementFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {

                // Get access to the GameBoardState and TurnTracker from the transaction
                val gameBoardState = stx.coreTransaction.outputsOfType<GameBoardState>().single()
                val turnTrackerStateRef = stx.coreTransaction.references.single()
                val turnTrackerState = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(turnTrackerStateRef)
                        .state.data

                // Build query criteria to retrieve that last evolution of the turn tracker we have on record.
                val lastTurnTrackerOnRecordStateAndRef = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
                        .state.data
                if (lastTurnTrackerOnRecordStateAndRef.linearId != turnTrackerState.linearId) {
                    throw FlowException("The TurnTracker included in the transaction is not correct for this game or turn.")
                }
                if (!gameBoardState.isValid(lastTurnTrackerOnRecordStateAndRef)) {
                    throw FlowException("The turn tracker state does not point back to the GameBoardState")
                }

                // Get all of the settlements currently allocated to this player
                val currentSettlementsBelongingToPlayer = serviceHub.vaultService
                        .queryBy<SettlementState>()
                        .states
                        .filter { it.state.data.owner == counterpartySession.counterparty }

                if (!turnTrackerState.setUpRound1Complete && currentSettlementsBelongingToPlayer.isNotEmpty()) {
                    throw FlowException("The current player has already built an initial settlement in round 1.")
                } else if (turnTrackerState.setUpRound1Complete && currentSettlementsBelongingToPlayer.size > 1) {
                    throw FlowException("The current player has already built an initial settlement in round 2.")
                }

                // Ensure that the player proposing the build of a settlement is currently the player whose turn it is.
                if (counterpartySession.counterparty.owningKey != gameBoardState
                                .players[lastTurnTrackerOnRecordStateAndRef.currTurnIndex].owningKey) {
                    throw FlowException("Only the current player may propose the next move.")
                }

                if (turnTrackerState.setUpRound2Complete) {
                    throw FlowException("You should be using the end turn function")
                }
            }
        }

        // Sign the transaction
        val txWeJustSignedId = subFlow(signedTransactionFlow)

        // Run the ReceiveFinalityFlow flow to finalize the transaction.
        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}
