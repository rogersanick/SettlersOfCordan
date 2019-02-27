package com.oracleService.flows

import co.paralleluniverse.fibers.Suspendable
import com.oracleClient.GetRandomDiceRollValues
import com.oracleService.contracts.DiceRollContract
import com.oracleService.service.Oracle
import com.oracleService.state.DiceRollState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@InitiatedBy(GetRandomDiceRollValues::class)
@InitiatingFlow
class DiceRollRequestHandler(val session: FlowSession): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        try {
            // Get data from the requesting node.
            val untrustworthyData = session.receive<List<UniqueIdentifier>>()
            val data = untrustworthyData.unwrap { it }

            // Get random dice rolls from the oracle
            val diceRoll1 = serviceHub.cordaService(Oracle::class.java).getRandomDiceRoll()
            val diceRoll2 = serviceHub.cordaService(Oracle::class.java).getRandomDiceRoll()

            // Get the notary identity and add it to the transaction
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val tb = TransactionBuilder(notary)

            // Create a diceRollState to track which random dice rolls correspond to specific game and turn
            val diceRollState = DiceRollState(diceRoll1, diceRoll2, data[0], data[1], listOf(ourIdentity))
            tb.addOutputState(diceRollState, DiceRollContract.ID)

            // Add the 'RollDice' command to the transaction
            tb.addCommand(DiceRollContract.Commands.RollDice(), listOf(ourIdentity.owningKey))

            val finalizedDiceRollState = diceRollState.copy()
            session.send(finalizedDiceRollState)

            tb.verify(serviceHub)
            val stx = serviceHub.signInitialTransaction(tb)
            subFlow(FinalityFlow(stx, listOf()))
        } catch (e: Error) {
            throw FlowException(e.message)
        }
    }

}