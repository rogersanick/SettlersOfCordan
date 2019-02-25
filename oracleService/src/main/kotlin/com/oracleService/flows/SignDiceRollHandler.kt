package com.oracleService.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.TurnTrackerState
import com.oracleClient.flows.SignDiceRollFlow
import com.oracleService.contracts.DiceRollContract
import com.oracleService.state.DiceRollState
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.unwrap
import java.util.*

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
        val oracleCopyOfDiceRollWeHaveOnRecord = allDiceRollsOracleHasOnRecord.first().state.data

        requireThat {
            "The first dice rolled is the same the oracle record" using (outputDiceRollInTransaction.randomRoll1 == oracleCopyOfDiceRollWeHaveOnRecord.randomRoll1)
            "The second dice rolled is the same the oracle record" using (outputDiceRollInTransaction.randomRoll2 == oracleCopyOfDiceRollWeHaveOnRecord.randomRoll2)
            "The gameBoardState referenced is the same as we have on record" using (outputDiceRollInTransaction.gameBoardStateUniqueIdentifier == oracleCopyOfDiceRollWeHaveOnRecord.gameBoardStateUniqueIdentifier)
            "The gameBoardState referenced is the same as we have on record" using (outputDiceRollInTransaction.turnTrackerUniqueIdentifier == oracleCopyOfDiceRollWeHaveOnRecord.turnTrackerUniqueIdentifier)
            "The turn tracker referenced is the same and has not been used previously" using (allDiceRollsOracleHasOnRecord.filter { it.state.data.turnTrackerUniqueIdentifier == outputDiceRollInTransaction.turnTrackerUniqueIdentifier }.isEmpty())
        }

        val myKey = serviceHub.myInfo.legalIdentities.first().owningKey

        fun isRollDiceCommandAndIAmSigner(elem: Any) = when {
            elem is Command<*> && elem.value is DiceRollContract.Commands.RollDice -> {
                myKey in elem.signers
            }
            else -> false
        }

        // Is it a Merkle tree we are willing to sign over?
        val isValidMerkleTree = ftx.checkWithFun(::isRollDiceCommandAndIAmSigner)

        if (isValidMerkleTree) {
            return serviceHub.createSignature(ftx, myKey)
        } else {
            throw java.lang.IllegalArgumentException("Oracle signature requested over invalid transaction.")
        }

    }

}