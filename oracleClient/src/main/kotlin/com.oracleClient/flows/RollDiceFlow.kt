package com.oracleClient.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.TurnTrackerState
import com.oracleService.contracts.DiceRollContract
import com.oracleService.state.DiceRollState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.util.*
import java.util.function.Predicate
import kotlin.collections.ArrayList

// ******************
// * Roll Dice Flow *
// ******************

@InitiatingFlow
@StartableByRPC
class RollDiceFlow(val turnTrackerStateLinearId: UniqueIdentifier, val gameBoardState: GameBoardState, val oracle: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val oracleSession = initiateFlow(oracle)
        oracleSession.send(listOf(turnTrackerStateLinearId, gameBoardState.linearId))

        val diceRoll = oracleSession.receive<DiceRollState>().unwrap { it }
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val tb = TransactionBuilder(notary)

        tb.addOutputState(diceRoll.copy(participants = listOf(ourIdentity, oracle)), DiceRollContract.ID)
        tb.addCommand(DiceRollContract.Commands.RollDice(), listOf(ourIdentity.owningKey, oracle.owningKey))

        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        val ftx = ptx.buildFilteredTransaction(Predicate {
            when (it) {
                is Command<*> -> oracle.owningKey in it.signers && it.value is DiceRollContract.Commands.RollDice
                else -> false
            }
        })

        val oracleSignature = subFlow(SignDiceRollFlow(oracle, ftx))
        val stx = ptx.withAdditionalSignature(oracleSignature)

        return subFlow(FinalityFlow(stx, gameBoardState.players.toSet().map { initiateFlow(it) }))
    }
}

