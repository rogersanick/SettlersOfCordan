package com.r3.cordan.primary.flows.random

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.oracle.client.flows.GetRandomDiceRollValues
import com.r3.cordan.oracle.client.contracts.DiceRollContract
import com.r3.cordan.oracle.client.states.DiceRollState
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.states.structure.GameBoardState
import com.r3.cordan.primary.states.turn.TurnTrackerState
import net.corda.core.contracts.ReferencedStateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// ******************
// * Roll Dice Flow *
// ******************

/**
 * This flow uses the DiceRoll oracle. It gets a new, randomly generated DiceRollState
 * associated with a specific turn tracker as well as a specific GameBoardState. This
 * DiceRollState is then used to facilitate the collection of resources and the advancement
 * of the game.
 */

@InitiatingFlow
@StartableByRPC
class RollDiceFlow(val gameBoardLinearId: UniqueIdentifier, val diceRollState: DiceRollState? = null) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {

        val gameBoardStateAndRef = serviceHub.vaultService
                .querySingleState<GameBoardState>(gameBoardLinearId)
        val gameBoardReferenceStateAndRef = ReferencedStateAndRef(gameBoardStateAndRef)
        val gameBoardState = gameBoardStateAndRef.state.data

        val turnTrackerState = serviceHub.vaultService
                .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
                .state.data
        if (!gameBoardState.isValid(turnTrackerState)) {
            throw FlowException("The turn tracker state does not point back to the GameBoardState")
        }
        val turnTrackerStateLinearId = turnTrackerState.linearId

        val oracleLegalName = CordaX500Name("Oracle", "New York", "US")
        val oracle = serviceHub.networkMapCache.getNodeByLegalName(oracleLegalName)!!.legalIdentities.single()

        val diceRoll = diceRollState ?: subFlow(GetRandomDiceRollValues(turnTrackerStateLinearId, gameBoardState.linearId, gameBoardState.players, oracle))

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val tb = TransactionBuilder(notary)

        tb.addOutputState(DiceRollState(diceRoll))
        tb.addReferenceState(gameBoardReferenceStateAndRef)
        tb.addCommand(DiceRollContract.Commands.RollDice(), gameBoardState.playerKeys())

        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        val sessions = (gameBoardState.players - ourIdentity).toSet().map { initiateFlow(it) }
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(RollDiceFlow::class)
open class RollDiceFlowResponder(internal val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {

                // TODO not assume that there is a single game in flight
                val gameBoardState = serviceHub.vaultService
                        .querySingleState<GameBoardState>(stx.coreTransaction.references.single())
                        .state.data
                val gameBoardLinearId = gameBoardState.linearId

                val turnTrackerState = serviceHub.vaultService
                        .querySingleState<TurnTrackerState>(gameBoardState.turnTrackerLinearId)
                        .state.data
                val turnTrackerLinearId = turnTrackerState.linearId

                if (!gameBoardState.isValid(turnTrackerState)) {
                    throw FlowException("The turn tracker state does not point back to the GameBoardState")
                }

                val diceRollState = stx.coreTransaction.outputsOfType<DiceRollState>().single()

                if (diceRollState.turnTrackerUniqueIdentifier != turnTrackerState.linearId) {
                    throw FlowException("Only the current player may roll the random.")
                }

                if (diceRollState.gameBoardLinearId != gameBoardState.linearId) {
                    throw FlowException("The random roll must have been generated for this game.")
                }

                if (!diceRollState.signedData.isValid(
                                byteArrayOf(
                                        diceRollState.randomRoll1.toByte(),
                                        diceRollState.randomRoll2.toByte(),
                                        turnTrackerLinearId.hashCode().toByte(),
                                        gameBoardLinearId.hashCode().toByte()))) {
                    throw FlowException("This random roll was not generated by the oracle")
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(
                otherSideSession = counterpartySession,
                expectedTxId = txWeJustSignedId.id,
                statesToRecord = StatesToRecord.ALL_VISIBLE
        ))
    }
}