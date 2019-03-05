package com.oracleService.flows

import co.paralleluniverse.fibers.Suspendable
import com.oracleClient.contracts.DiceRollContract
import com.oracleClient.flows.SignDiceRollFlow
import com.oracleClient.state.DiceRollState
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.unwrap

@InitiatedBy(SignDiceRollFlow::class)
class SignDiceRollHandler(val session: FlowSession): FlowLogic<TransactionSignature>() {

    @Suspendable
    override fun call(): TransactionSignature {
        val ftx = session.receive<FilteredTransaction>().unwrap { it }

        // checks if the partial Merkle tree is valid
        ftx.verify()

        // Get the dice roll included in the transaction.
        val outputDiceRollInTransaction = ftx.outputsOfType<DiceRollState>().single()

        val allDiceRollsOracleHasOnRecord = serviceHub.vaultService.queryBy<DiceRollState>().states

        if (allDiceRollsOracleHasOnRecord.isNotEmpty()) {

            val oracleCopyOfDiceRollWeHaveOnRecord = allDiceRollsOracleHasOnRecord.first().state.data

            requireThat {
                "The first dice rolled is the same the oracle record" using (outputDiceRollInTransaction.randomRoll1 == oracleCopyOfDiceRollWeHaveOnRecord.randomRoll1)
                "The second dice rolled is the same the oracle record" using (outputDiceRollInTransaction.randomRoll2 == oracleCopyOfDiceRollWeHaveOnRecord.randomRoll2)
                "The gameBoardState referenced is the same as we have on record" using (outputDiceRollInTransaction.gameBoardStateUniqueIdentifier == oracleCopyOfDiceRollWeHaveOnRecord.gameBoardStateUniqueIdentifier)
                "The gameBoardState referenced is the same as we have on record" using (outputDiceRollInTransaction.turnTrackerUniqueIdentifier == oracleCopyOfDiceRollWeHaveOnRecord.turnTrackerUniqueIdentifier)
                "The turn tracker referenced is the same and has not been used previously" using (allDiceRollsOracleHasOnRecord.filter { it.state.data.turnTrackerUniqueIdentifier == outputDiceRollInTransaction.turnTrackerUniqueIdentifier }.size == 1)
            }

        }

        // Check if the state is valid using our Signature
        val byteArrayOfDataToSign = byteArrayOf(outputDiceRollInTransaction.randomRoll1.toByte(), outputDiceRollInTransaction.randomRoll2.toByte(), outputDiceRollInTransaction.turnTrackerUniqueIdentifier.hashCode().toByte(), outputDiceRollInTransaction.gameBoardStateUniqueIdentifier.hashCode().toByte())
        val signedDataWithCertFromOutputDiceRoll = ourIdentity.signWithCert { DigitalSignatureWithCert(ourIdentityAndCert.certificate, byteArrayOfDataToSign) }

        // Check if the command is correct and the oracle is a signer
        val myKey = serviceHub.myInfo.legalIdentities.first().owningKey
        fun isRollDiceCommandAndIAmSigner(elem: Any) = when {
            elem is TransactionState<*> -> (elem.data as DiceRollState).signedDataWithOracleCert == signedDataWithCertFromOutputDiceRoll.id
            elem is Command<*> && elem.value is DiceRollContract.Commands.RollDice -> {
                myKey in elem.signers
            }
            else -> false
        }

        // Is it a Merkle tree we are willing to sign over?
        val isValidMerkleTree = ftx.checkWithFun(::isRollDiceCommandAndIAmSigner)
        if (isValidMerkleTree) {
            val transactionSignature = serviceHub.createSignature(ftx, myKey)
            session.send(transactionSignature)
            return transactionSignature
        } else {
            throw java.lang.IllegalArgumentException("Oracle signature requested over invalid transaction.")
        }

    }

}