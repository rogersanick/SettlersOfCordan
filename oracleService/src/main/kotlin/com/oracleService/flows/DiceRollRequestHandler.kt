package com.oracleService.flows

import co.paralleluniverse.fibers.Suspendable
import com.oracleClient.flows.GetRandomDiceRollValues
import com.oracleClient.state.DiceRollState
import com.oracleService.service.OracleService
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.utilities.unwrap

@InitiatedBy(GetRandomDiceRollValues::class)
class DiceRollRequestHandler(val session: FlowSession): FlowLogic<Unit>() {

    // TODO: Decouple tightly integrated oracleClient and oracleService.
    @Suspendable
    override fun call() {
        try {
            // Get data from the requesting node.
            val untrustworthyData = session.receive<List<UniqueIdentifier>>()
            val data = untrustworthyData.unwrap { it }

            // Get random dice rolls from the oracle
            val diceRoll1 = serviceHub.cordaService(OracleService::class.java).getRandomDiceRoll()
            val diceRoll2 = serviceHub.cordaService(OracleService::class.java).getRandomDiceRoll()

            if (serviceHub.vaultService.queryBy(DiceRollState::class.java).states.filter { it.state.data.turnTrackerUniqueIdentifier == data[0] }.isNotEmpty()) {
                throw FlowException("A dice roll has previously been generated for this turn")
            }

            val byteArrayOfDataToSign = byteArrayOf(diceRoll1.toByte(), diceRoll2.toByte(), data[0].hashCode().toByte(), data[1].hashCode().toByte())
            val signatureOfDataSignedByTheOracle = ourIdentity.signWithCert { DigitalSignatureWithCert(ourIdentityAndCert.certificate, byteArrayOfDataToSign) }
            session.send(arrayListOf(diceRoll1, diceRoll2))
            session.send(signatureOfDataSignedByTheOracle)
        } catch (e: Error) {
            throw FlowException(e.message)
        }
    }

}