package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.GatherPhaseContract
import com.contractsAndStates.states.*
import com.oracleClient.contracts.DiceRollContract
import com.oracleClient.state.DiceRollState
import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import com.r3.corda.sdk.token.workflow.flows.RedeemToken
import com.r3.corda.sdk.token.workflow.selection.TokenSelection
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * This flow will allow users to exchange their existing assets at a port at the specified exchange rate. It is
 * currently unimplemented and is a copy / paste of the IssueTokenFlow. The issue here surrounds the burning of
 * the tokens - which is currently impossible. A potential interim solution might be issuing a new identity on the
 * network and having them act as an inactive 'burnt-token-collector'.
 */

@InitiatingFlow
@StartableByRPC
class TradeWithPortFlow(val gameBoardLinearId: UniqueIdentifier, val indexOfPort: Int, val coordinateOfPort: Int, val resourceAmount: Long, val resourceType: String): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Step 1. Get reference to the notary and oracle
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Retrieve the Game Board State from the vault.
        val queryCriteriaForGameBoardState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardLinearId))
        val gameBoardStateAndRef = serviceHub.vaultService.queryBy<GameBoardState>(queryCriteriaForGameBoardState).states.single()
        val gameBoardState = gameBoardStateAndRef.state.data

        // Step 3. Retrieve the Turn Tracker State from the vault
        val queryCriteriaForTurnTrackerState = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardState.turnTrackerLinearId))
        val turnTrackerStateAndRef = serviceHub.vaultService.queryBy<TurnTrackerState>(queryCriteriaForTurnTrackerState).states.single()

        // Step 4. Get access to the port with which the user wishes to trade
        val portToBeTradedWith = gameBoardState.ports.filter { it.accessPoints.any { accessPoint ->  accessPoint.hexTileIndex == indexOfPort && accessPoint.hexTileCoordinate.contains(coordinateOfPort) } }.single().portTile

        // Step 5. Generate an exit for the tokens that will be consumed by the port.
        val resourceTypeParsed = Resource.getInstance(resourceType)
        val amountOfResourceType = Amount(resourceAmount, resourceTypeParsed)
        val tb = TransactionBuilder()
        val tokenSelection = TokenSelection(serviceHub)
        RedeemToken

        // Step 6. Generate a list of all currencies amounts that will be issued.
        tb.addOutputState(amountOfResourceType issuedBy ourIdentity heldBy ourIdentity )
        tb.addCommand(IssueTokenCommand(amountOfResourceType.token issuedBy ourIdentity))

        // Step 9. Add the gather resources command and verify the transaction
        val commandSigners = gameBoardState.players.map {it.owningKey}
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

@InitiatedBy(TradeWithPortFlow::class)
open class TradeWithPortFlowResponder(val counterpartySession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val listOfTokensIssued = stx.coreTransaction.outputsOfType<FungibleState<*>>().toMutableList()
                val gameBoardState = serviceHub.vaultService.queryBy<GameBoardState>(QueryCriteria.VaultQueryCriteria(stateRefs = stx.references)).states.single().state.data
                val turnTrackerState = serviceHub.vaultService.queryBy<TurnTrackerState>().states.single().state.data
                val diceRollState = serviceHub.vaultService.queryBy<DiceRollState>(QueryCriteria.VaultQueryCriteria(stateRefs = stx.inputs)).states.single().state.data
                val listOfValidSettlements = serviceHub.vaultService.queryBy<SettlementState>().states.filter {
                    val diceRollTotal = diceRollState.randomRoll1 + diceRollState.randomRoll2
                    val adjacentHexTileIndex1 = gameBoardState.hexTiles[(gameBoardState.hexTiles[it.state.data.hexTileIndex].sides[it.state.data.hexTileCoordinate]) ?: 7].roleTrigger == diceRollTotal
                    val adjacentHexTileIndex2 = gameBoardState.hexTiles[(gameBoardState.hexTiles[it.state.data.hexTileIndex].sides[if (it.state.data.hexTileCoordinate - 1 < 0) 5 else it.state.data.hexTileCoordinate - 1]) ?: 7].roleTrigger == diceRollTotal
                    val primaryHexTile = gameBoardState.hexTiles[it.state.data.hexTileIndex].roleTrigger == diceRollTotal
                    adjacentHexTileIndex1 || adjacentHexTileIndex2 || primaryHexTile
                }

                val gameCurrenciesThatShouldHaveBeenClaimed = arrayListOf<GameCurrencyToClaim>()
                for (result in listOfValidSettlements) {
                    val settlementState = result.state.data
                    val hexTile = gameBoardState.hexTiles[result.state.data.hexTileIndex]
                    val ownerIndex = gameBoardState.players.indexOf(settlementState.owner)
                    gameCurrenciesThatShouldHaveBeenClaimed.add(GameCurrencyToClaim(hexTile.resourceType, ownerIndex))
                }

                // Consolidate the list so that they is only one instance of a given token type issued with the appropriate amount.
                val reducedListOfGameCurrencyToClaim = mutableMapOf<GameCurrencyToClaim, Int>()
                gameCurrenciesThatShouldHaveBeenClaimed.forEach{
                    if (reducedListOfGameCurrencyToClaim.containsKey(it)) reducedListOfGameCurrencyToClaim[it] = reducedListOfGameCurrencyToClaim[it]!!.plus(1)
                    else reducedListOfGameCurrencyToClaim[it] = 1
                }

                // Convert each gameCurrentToClaim into a valid fungible token.
                val listOfTokensThatShouldHaveBeenIssued = reducedListOfGameCurrencyToClaim.map {
                    amount(it.value, Resource.getInstance(it.key.resourceType)) issuedBy gameBoardState.players[turnTrackerState.currTurnIndex] heldBy gameBoardState.players[it.key.ownerIndex]
                }

                "The correct number of resources must be produced for each respective party" using (listOfTokensThatShouldHaveBeenIssued.filter {
                    listOfTokensIssued.indexOf(it) == -1
                }.isEmpty() && listOfTokensIssued.size == listOfTokensThatShouldHaveBeenIssued.size)

            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }

}