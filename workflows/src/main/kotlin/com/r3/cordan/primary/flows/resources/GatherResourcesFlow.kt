package com.r3.cordan.primary.flows.resources

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.cordan.oracle.client.contracts.DiceRollContract
import com.r3.cordan.oracle.client.states.RollTrigger
import com.r3.corda.lib.tokens.workflows.utilities.addTokenTypeJar
import com.r3.cordan.primary.contracts.resources.GatherPhaseContract
import com.r3.cordan.primary.flows.addIssueTokens
import com.r3.cordan.primary.flows.queryDiceRoll
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.states.board.HexTile
import com.r3.cordan.primary.states.resources.GameCurrencyState
import com.r3.cordan.primary.states.resources.forGameBoard
import com.r3.cordan.primary.states.robber.RobberState
import com.r3.cordan.primary.states.structure.GameBoardState
import com.r3.cordan.primary.states.structure.SettlementState
import com.r3.cordan.primary.states.turn.TurnTrackerState
import net.corda.core.contracts.FungibleState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.toMultiMap
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.VaultService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// *************************
// * Gather Resources Flow *
// *************************

/**
 * This flow allows nodes to self-issue tokens where they are able to prove that
 * they have the appropriate settlement states to enable them to do so.
 */
@InitiatingFlow(version = 1)
@StartableByRPC
class GatherResourcesFlow(val gameBoardLinearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {


        // Step 1. Retrieve the Game Board State from the vault.
        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 1.5 Check if there is an active robber state.
        val robberStateAndRef = serviceHub.vaultService.querySingleState<RobberState>(gameBoardState.robberLinearId)
        val robberState = robberStateAndRef.state.data

        if (robberState.active) {
            throw FlowException("There is an active robber state. You must move the robber and invoke its consequences using ApplyRobberFlow before proceeding to the Gather Phase.")
        }

        // Step 2. Get reference to the notary and oracle
        val notary = gameBoardStateAndRef.state.notary

        // Step 3. Retrieve the Turn Tracker State from the vault
        val turnTrackerStateAndRef = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
        if (!gameBoardState.isValid(turnTrackerStateAndRef.state.data)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }
        // Step 4. Retrieve the Dice Roll State from the vault
        val diceRollStateAndRef = serviceHub.vaultService
                .queryDiceRoll(gameBoardLinearId)
        val diceRollState = diceRollStateAndRef.state.data

        if (diceRollState.isRobberTotal()) {
            throw FlowException("The last active dice roll has a value of 7. You must move the robber and apply its consequences using MoveRobberFlow before continuing.")
        }

        // Step 5. Create a transaction builder
        val tb = TransactionBuilder(notary = notary)

        // Step 6. Find valid settlements for which we will be issuing resources.
        // Generate a list of all currencies amounts that will be issued.
        // Consolidate the list so that they is only one instance of a given token type issued with the appropriate
        // amount. This is required right now as multiple issuances of the same token type cause an error with
        // transaction state grouping.
        val tokensToIssue = serviceHub.vaultService
                .getTokensToIssue(gameBoardState, diceRollState.getRollTrigger(), ourIdentity)

        // Step 7. Add commands to issue the appropriate types of resources. Convert the gameCurrencyToClaim to a set
        // to prevent duplicate commands.
        addIssueTokens(tb, tokensToIssue, gameBoardState.playerKeys())
        addTokenTypeJar(tokensToIssue, tb)

        // Step 8. Add reference states for turn tracker and game board. Add the dice roll as an input
        // state so that the counter-party may verify the correct number of resources have been issued.
        tb.addInputState(diceRollStateAndRef)
        tb.addReferenceState(ReferencedStateAndRef(gameBoardStateAndRef))
        tb.addReferenceState(ReferencedStateAndRef(turnTrackerStateAndRef))

        // Step 9. Add the gather resources command and verify the transaction
        val commandSigners = gameBoardState.playerKeys()
        tb.addCommand(GatherPhaseContract.Commands.IssueResourcesToAllPlayers(), commandSigners)
        tb.addCommand(DiceRollContract.Commands.ConsumeDiceRoll(), commandSigners)
        tb.verify(serviceHub)

        // Step 10. Collect the signatures and sign the transaction
        val ptx = serviceHub.signInitialTransaction(tb)
        val sessions = (gameBoardState.players - ourIdentity).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx, sessions, StatesToRecord.ALL_VISIBLE))
    }
}

@InitiatedBy(GatherResourcesFlow::class)
open class GatherResourcesFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val listOfTokensIssued = stx.coreTransaction.outputsOfType<FungibleState<*>>().toMutableList()
                val gameBoardState = serviceHub.vaultService
                        .querySingleState<GameBoardState>(stx.references)
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
                val diceRollState = serviceHub.vaultService
                        .queryDiceRoll(gameBoardState.linearId)
                        .state.data
                val listOfTokensThatShouldHaveBeenIssued = serviceHub.vaultService
                        .getTokensToIssue(gameBoardState, diceRollState.getRollTrigger(), counterpartySession.counterparty)

                if (!listOfTokensThatShouldHaveBeenIssued.containsAll(listOfTokensIssued)
                                || listOfTokensIssued.size != listOfTokensThatShouldHaveBeenIssued.size) {
                    throw FlowException("The correct number of resources must be produced for each respective party")
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

internal fun VaultService.getTokensToIssue(gameBoardState: GameBoardState, rollTrigger: RollTrigger, issuer: Party) = gameBoardState
        .getTilesBy(rollTrigger, false)
        .flatMap { tile ->
            if (tile.resourceType.resourceYielded == null) {
                throw FlowException("A tile with a rollTrigger must has a resource")
            }
            HexTile.getAllCorners().map { tile.getSettlementOn(it) to tile.resourceType.resourceYielded!! }
        }
        .filter { it.first != null }
        .map { it.first!! to it.second }
        .map { pair ->
            // TODO Single call, not so efficient. Improve with .zip?
            val settlement = querySingleState<SettlementState>(pair.first)
                    .state.data
            settlement.owner to pair.second to settlement.resourceAmountClaim

        }
        .toMultiMap()
        .mapValues { it.value.sum() }
        .map { entry ->
            entry.value of entry.key.second issuedBy issuer heldBy entry.key.first forGameBoard gameBoardState.linearId
        }
