package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.GatherPhaseContract
import com.contractsAndStates.states.*
import com.oracleClientStatesAndContracts.contracts.DiceRollContract
import com.oracleClientStatesAndContracts.states.DiceRollState
import com.oracleClientStatesAndContracts.states.RollTrigger
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.issue.addIssueTokens
import com.r3.corda.lib.tokens.workflows.utilities.addTokenTypeJar
import net.corda.core.contracts.FungibleState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.internal.toMultiMap
import net.corda.core.node.services.VaultService
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// ********************
// * Issue Trade Flow *
// ********************

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

        // Step 2. Get reference to the notary and oracle
        val notary = gameBoardStateAndRef.state.notary

        // Step 3. Retrieve the Turn Tracker State from the vault
        // TODO how do we prevent players issuing themselves resources twice by calling this flow once more?
        val turnTrackerStateAndRef = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
        if (gameBoardState.isValid(turnTrackerStateAndRef.state.data)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }
        // Step 4. Retrieve the Dice Roll State from the vault
        val diceRollStateAndRef = serviceHub.vaultService
                .querySingleState<DiceRollState>(gameBoardLinearId)
        val diceRollState = diceRollStateAndRef.state.data

        // Step 5. Create a transaction builder
        val tb = TransactionBuilder(notary = notary)

        // Step 6. Find valid settlements for which we will be issuing resources.
        // Generate a list of all currencies amounts that will be issued.
        // Consolidate the list so that they is only one instance of a given token type issued with the appropriate
        // amount. This is required right now as multiple issuances of the same token type cause an error with
        // transaction state grouping.
        val tokensToIssue = serviceHub.vaultService
                .getTokensToIssue(gameBoardState, diceRollState.getRollTrigger())

        // Step 7. Add commands to issue the appropriate types of resources. Convert the gameCurrencyToClaim to a set
        // to prevent duplicate commands.
        addIssueTokens(tb, tokensToIssue)
        addTokenTypeJar(tokensToIssue, tb)

        // Step 8. Add reference states for turn tracker and game board. Add the dice roll as an input
        // state so that the counter-party may verify the correct number of resources have been issued.
        tb.addInputState(diceRollStateAndRef)
        tb.addReferenceState(ReferencedStateAndRef(gameBoardStateAndRef))
        tb.addReferenceState(ReferencedStateAndRef(turnTrackerStateAndRef))

        // Step 9. Add the gather resources command and verify the transaction
        val commandSigners = gameBoardState.players.map { it.owningKey }
        tb.addCommand(GatherPhaseContract.Commands.IssueResourcesToAllPlayers(), commandSigners)
        tb.addCommand(DiceRollContract.Commands.ConsumeDiceRoll(), commandSigners)
        tb.verify(serviceHub)

        // Step 10. Collect the signatures and sign the transaction
        val ptx = serviceHub.signInitialTransaction(tb)
        val sessions = (gameBoardState.players - ourIdentity).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))
        return subFlow(FinalityFlow(stx, sessions))
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
                val turnTrackerState = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
                        .state.data
                if (gameBoardState.isValid(turnTrackerState)) {
                    throw FlowException("The turn tracker state does not point back to the GameBoardState")
                }
                val diceRollState = serviceHub.vaultService
                        .querySingleState<DiceRollState>(stx.inputs)
                        .state.data
                val listOfTokensThatShouldHaveBeenIssued = serviceHub.vaultService
                        .getTokensToIssue(gameBoardState, diceRollState.getRollTrigger())

                if ((listOfTokensThatShouldHaveBeenIssued.all {
                            listOfTokensIssued.indexOf(it) != -1
                        } || listOfTokensIssued.size != listOfTokensThatShouldHaveBeenIssued.size)) {
                    throw FlowException("The correct number of resources must be produced for each respective party")
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}

internal fun VaultService.getTokensToIssue(gameBoardState: GameBoardState, rollTrigger: RollTrigger) = gameBoardState
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
            entry.value of entry.key.second issuedBy entry.key.first heldBy entry.key.first
        }

data class GameCurrencyToClaim(
        val resourceType: HexTileType,
        val ownerIndex: Int
)
