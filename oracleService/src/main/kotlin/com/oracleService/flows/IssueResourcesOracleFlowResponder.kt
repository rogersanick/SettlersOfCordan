package com.oracleService.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.states.SettlementState
import com.flows.IssueResourcesFlow
import com.flows.IssueResourcesFlowResponder
import com.oracleClient.contracts.DiceRollContract
import com.oracleClient.flows.GetRandomDiceRollValues
import com.oracleService.service.OracleService
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

@InitiatedBy(IssueResourcesFlow::class)
class IssueResourcesOracleFlowResponder(val session: FlowSession): IssueResourcesFlowResponder(session) {

    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(session) {
            override fun checkTransaction(stx: SignedTransaction) {
                val consumeDiceRollCommand = stx.tx.commands.filter { it.value is DiceRollContract.Commands.ConsumeDiceRoll }
                assert(consumeDiceRollCommand.size == 1)
                assert(consumeDiceRollCommand.single().signers.contains(ourIdentity.owningKey))
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = session, expectedTxId = txWeJustSignedId.id))
    }

}