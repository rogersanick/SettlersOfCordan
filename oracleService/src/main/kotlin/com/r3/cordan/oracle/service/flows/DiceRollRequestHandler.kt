package com.r3.cordan.oracle.service.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.cordan.oracle.client.flows.GetRandomDiceRollValues
import com.r3.cordan.oracle.client.states.DiceRollState
import com.r3.cordan.oracle.service.service.OracleService
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.utilities.unwrap

@InitiatedBy(GetRandomDiceRollValues::class)
class DiceRollRequestHandler(val session: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        try {
            // Get data from the requesting node.
            val untrustworthyData = session.receive<List<UniqueIdentifier>>()
            val data = untrustworthyData.unwrap { it }

            if (serviceHub.vaultService.queryBy(DiceRollState::class.java).states.filter { it.state.data.turnTrackerUniqueIdentifier == data[0] }.isNotEmpty()) {
                throw FlowException("A random roll has previously been generated for this turn")
            }

            // Get random random rolls from the oracle
            val diceRoll1 = serviceHub.cordaService(OracleService::class.java).getRandomDiceRoll()
            val diceRoll2 = serviceHub.cordaService(OracleService::class.java).getRandomDiceRoll()

            val byteArrayOfDataToSign = byteArrayOf(diceRoll1.toByte(), diceRoll2.toByte(), data[0].hashCode().toByte(), data[1].hashCode().toByte())
            val signatureOfDataSignedByTheOracle = serviceHub.keyManagementService.sign(byteArrayOfDataToSign, ourIdentity.owningKey)
            session.send(arrayListOf(diceRoll1, diceRoll2))
            session.send(signatureOfDataSignedByTheOracle)
        } catch (e: Error) {
            throw FlowException(e.message)
        }
    }

}