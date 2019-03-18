package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.GatherPhaseContract
import com.contractsAndStates.states.*
import com.oracleClient.contracts.DiceRollContract
import com.oracleClient.state.DiceRollState
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.sdk.token.contracts.OwnedTokenAmountContract
import net.corda.sdk.token.contracts.commands.Issue
import net.corda.sdk.token.contracts.commands.OwnedTokenCommand
import net.corda.sdk.token.contracts.states.OwnedTokenAmount
import net.corda.sdk.token.contracts.types.EmbeddableToken
import net.corda.sdk.token.contracts.types.Issued
import net.corda.sdk.token.contracts.utilities.AMOUNT
import net.corda.sdk.token.contracts.utilities.issuedBy
import net.corda.sdk.token.contracts.utilities.ownedBy
import java.lang.IllegalArgumentException

// *************************************
// * Initial Settlement Placement Flow *
// *************************************

// TODO: Add initialization service that prints Settlers Of CorDan to the console

@InitiatingFlow
@StartableByRPC
class IssueResourcesFlow(val gameBoardLinearId: UniqueIdentifier, val oracle: Party): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Step 1. Get reference to the notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Retrieve the Game Board State from the vault.
        val queryCriteriaForGameBoardState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardLinearId))
        val gameBoardStateAndRef = serviceHub.vaultService.queryBy<GameBoardState>(queryCriteriaForGameBoardState).states.single()
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 3. Retrieve the Turn Tracker State from the vault
        val queryCriteriaForTurnTrackerState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardState.turnTrackerLinearId))
        val turnTrackerStateAndRef = serviceHub.vaultService.queryBy<TurnTrackerState>(queryCriteriaForTurnTrackerState).states.single()

        // Step 4. Retrieve the Dice Roll State from the vault
        val diceRollStateAndRef = serviceHub.vaultService.queryBy<DiceRollState>().states.single()
        val diceRollState = diceRollStateAndRef.state.data

        // Step 5. Create a transaction builder
        val tb = TransactionBuilder(notary = notary)

        // Step 6. Generate the appropriate resources
        val listOfValidSettlements = serviceHub.vaultService.queryBy<SettlementState>().states.filter {
            val diceRollTotal = diceRollState.randomRoll1 + diceRollState.randomRoll2
            val adjacentHexTileIndex1 = gameBoardState.hexTiles[(gameBoardState.hexTiles[it.state.data.hexTileIndex].sides[it.state.data.hexTileCoordinate]) ?: 7].roleTrigger == diceRollTotal
            val adjacentHexTileIndex2 = gameBoardState.hexTiles[(gameBoardState.hexTiles[it.state.data.hexTileIndex].sides[it.state.data.hexTileCoordinate + 1]) ?: 7].roleTrigger == diceRollTotal
            val primaryHexTile = gameBoardState.hexTiles[it.state.data.hexTileIndex].roleTrigger == diceRollTotal
            adjacentHexTileIndex1 || adjacentHexTileIndex2 || primaryHexTile
        }

        for (result in listOfValidSettlements) {
            val settlementState = result.state.data
            val hexTile = gameBoardState.hexTiles[result.state.data.hexTileIndex]
            val resourceType = hexTile.resourceType
            val gameCurrency = Resource.getInstance(resourceType)
            val resource = AMOUNT(settlementState.resourceAmountClaim, gameCurrency) issuedBy ourIdentity ownedBy settlementState.owner
            tb.addCommand(Issue(resource.amount.token), ourIdentity.owningKey)
            tb.addOutputState(resource, OwnedTokenAmountContract.contractId)
        }

        // Step 7. Add reference states for turn tracker and game board. Add input state for the dice roll.
        tb.addInputState(diceRollStateAndRef)
        tb.addReferenceState(ReferencedStateAndRef(gameBoardStateAndRef))
        tb.addReferenceState(ReferencedStateAndRef(turnTrackerStateAndRef))

        // Step 8. Add the gather resources command and verify the transaction
        val commandSigners = gameBoardState.players.map {it.owningKey}
        tb.addCommand(GatherPhaseContract.Commands.issueResourcesToAllPlayers(), commandSigners)
        tb.addCommand(DiceRollContract.Commands.ConsumeDiceRoll(), commandSigners)
        tb.verify(serviceHub)

        // Step 9. Collect the signatures and sign the transaction
        val ptx = serviceHub.signInitialTransaction(tb)
        val sessions = (gameBoardState.players - ourIdentity).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))
        return subFlow(FinalityFlow(stx, sessions))
    }

}

@InitiatedBy(IssueResourcesFlow::class)
open class IssueResourcesFlowResponder(val counterpartySession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val oracleLegalName = CordaX500Name("Oracle", "New York", "US")
                val oracleParty = serviceHub.networkMapCache.getNodeByLegalName(oracleLegalName)
                oracleParty?.legalIdentities?.first()?.owningKey

                val listOfTokensIssued = stx.coreTransaction.outputsOfType<OwnedTokenAmount<*>>().toMutableList()

                val gameBoardState = serviceHub.vaultService.queryBy<GameBoardState>(QueryCriteria.VaultQueryCriteria(stateRefs = stx.references)).states.single().state.data
                val turnTrackerState = serviceHub.vaultService.queryBy<TurnTrackerState>().states.single().state.data
                val diceRollState = serviceHub.vaultService.queryBy<DiceRollState>(QueryCriteria.VaultQueryCriteria(stateRefs = stx.inputs)).states.single().state.data
                val listOfValidSettlements = serviceHub.vaultService.queryBy<SettlementState>().states.filter { gameBoardState.hexTiles[it.state.data.hexTileIndex].roleTrigger == diceRollState.randomRoll1 + diceRollState.randomRoll2 }

                val listOfTokensThatShouldHaveBeenIssued = mutableListOf<OwnedTokenAmount<*>>()
                for (result in listOfValidSettlements) {
                    val settlementState = result.state.data
                    val hexTile = gameBoardState.hexTiles[settlementState.hexTileIndex]
                    val resourceType = hexTile.resourceType
                    val gameCurrency = Resource.getInstance(resourceType)
                    val amountOfResource = AMOUNT(settlementState.resourceAmountClaim, gameCurrency) issuedBy gameBoardState.players[turnTrackerState.currTurnIndex]
                    val ownedTokenAmount = OwnedTokenAmount(amountOfResource, settlementState.owner)
                    listOfTokensThatShouldHaveBeenIssued.add(ownedTokenAmount)
                }

                "The correct number of resources must be produced for each respective party" using (listOfTokensThatShouldHaveBeenIssued.filter {
                    listOfTokensIssued.indexOf(it) == -1
                }.isEmpty() && listOfTokensIssued.size == listOfTokensThatShouldHaveBeenIssued.size)

            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }

    // Unused utility function
    fun getIssuedCurrency(hexTileTerrainType: String, stx: SignedTransaction): MutableMap<AbstractParty, Amount<Issued<Resource>>> {

        val resourceOfTypeIssued = stx.coreTransaction.outputsOfType<OwnedTokenAmount<*>>().filter {
            it.amount.token.product == Resource.getInstance(hexTileTerrainType)
        }
        val mappingOfPlayerToResourcesClaimed = mutableMapOf<AbstractParty, Amount<Issued<Resource>>>()
        val ourIdentity = ourIdentity
        val gameBoardState = serviceHub.vaultService.queryBy<GameBoardState>().states.single().state.data
        gameBoardState.players.forEach {
            mappingOfPlayerToResourcesClaimed[it] = Amount(0, Resource.getInstance(hexTileTerrainType) issuedBy ourIdentity)
        }

        resourceOfTypeIssued.forEach {
            val ownedTokenAmount = it as OwnedTokenAmount<Resource>
            mappingOfPlayerToResourcesClaimed[ownedTokenAmount.owner]?.plus(ownedTokenAmount.amount)
        }

        return mappingOfPlayerToResourcesClaimed

    }

}
