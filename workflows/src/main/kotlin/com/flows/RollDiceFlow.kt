package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.states.GameBoardState
import com.oracleClient.contracts.DiceRollContract
import com.oracleClient.flows.GetRandomDiceRollValues
import com.oracleClient.flows.SignDiceRollFlow
import com.oracleClient.state.DiceRollState
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.util.function.Predicate

// ******************
// * Roll Dice Flow *
// ******************

@InitiatingFlow
@StartableByRPC
class RollDiceFlow(val turnTrackerStateLinearId: UniqueIdentifier, val gameBoardState: GameBoardState, val oracle: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val diceRoll = subFlow(GetRandomDiceRollValues(turnTrackerStateLinearId, gameBoardState, oracle))
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val tb = TransactionBuilder(notary)

        tb.addOutputState(DiceRollState(diceRoll))
        tb.addCommand(DiceRollContract.Commands.RollDice(), listOf(ourIdentity.owningKey, oracle.owningKey))

        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        val ftx = ptx.buildFilteredTransaction(Predicate {
            when (it) {
                is TransactionState<*> -> (it.data as DiceRollState).gameBoardStateUniqueIdentifier == diceRoll.gameBoardStateUniqueIdentifier
                        && (it.data as DiceRollState).turnTrackerUniqueIdentifier == diceRoll.turnTrackerUniqueIdentifier
                is Command<*> -> oracle.owningKey in it.signers && it.value is DiceRollContract.Commands.RollDice
                else -> false
            }
        })

        val oracleSignature = subFlow(SignDiceRollFlow(oracle, ftx))
        val stx = ptx.withAdditionalSignature(oracleSignature)

        val sessionsForFinalityFlow = (gameBoardState.players - ourIdentity + oracle).toSet().map { initiateFlow(it) }
        return subFlow(FinalityFlow(stx, sessionsForFinalityFlow))
    }
}

@InitiatedBy(RollDiceFlow::class)
class RollDiceFlowResponder(val counterpartySession: FlowSession): FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val diceRollState = stx.coreTransaction.outputsOfType<DiceRollState>().first()
                val gameBoardState = serviceHub.vaultService.queryBy<GameBoardState>().states.first().state.data

                if (diceRollState.turnTrackerUniqueIdentifier !== gameBoardState.turnTrackerLinearId) {
                    throw IllegalArgumentException("Only the current player may roll the dice.")
                }

                if (diceRollState.gameBoardStateUniqueIdentifier !== gameBoardState.linearId) {
                    throw IllegalArgumentException("The dice roll must have been generated for this game.")
                }

            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}