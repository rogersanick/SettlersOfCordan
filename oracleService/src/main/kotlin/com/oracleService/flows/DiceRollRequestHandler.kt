package com.oracleService.flows

import co.paralleluniverse.fibers.Suspendable
import com.oracleClient.flows.GetRandomDiceRollValues
import com.oracleService.service.*
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.hash
import net.corda.core.internal.signWithCert
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@InitiatedBy(GetRandomDiceRollValues::class)
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

            val byteArrayOfDataToSign = byteArrayOf(diceRoll1.toByte(), diceRoll2.toByte(), data[0].hashCode().toByte(), data[1].hashCode().toByte())
            val signatureOfDataSignedByTheOracle = ourIdentity.signWithCert { DigitalSignatureWithCert(ourIdentityAndCert.certificate, byteArrayOfDataToSign) }
            session.send(arrayListOf(diceRoll1, diceRoll2))
            session.send(signatureOfDataSignedByTheOracle.id)
        } catch (e: Error) {
            throw FlowException(e.message)
        }
    }

}