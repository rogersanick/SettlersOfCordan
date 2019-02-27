package com.oracleService.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.TurnTrackerState
import com.flows.RollDiceFlow
import com.oracleService.contracts.DiceRollContract
import com.oracleService.service.Oracle
import com.oracleService.state.DiceRollState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.util.*

@InitiatedBy(RollDiceFlow::class)
class DiceRollRequestHandler(val session: FlowSession): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val data = session.receive<List<UniqueIdentifier>>().unwrap { it }
        val diceRoll1 = serviceHub.cordaService(Oracle::class.java).getRandomDiceRoll()
        val diceRoll2 = serviceHub.cordaService(Oracle::class.java).getRandomDiceRoll()
        val tb = TransactionBuilder()
        val diceRollState = DiceRollState(diceRoll1, diceRoll2, data[0], data[1], listOf(ourIdentity))
        tb.addOutputState(diceRollState, DiceRollContract.ID)
        tb.verify(serviceHub)
        val stx = serviceHub.signInitialTransaction(tb)
        subFlow(FinalityFlow(stx, initiateFlow(ourIdentity)))
        session.send(diceRollState)
    }

}