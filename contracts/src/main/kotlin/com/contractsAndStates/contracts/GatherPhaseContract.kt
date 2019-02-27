package com.contractsAndStates.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction

// *************************
// * Gather Phase Contract *
// *************************

class GatherPhaseContract : Contract {
    companion object {
        const val ID = "com.contractsAndStates.contracts.GatherPhaseContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
    }

    interface Commands : CommandData {
        class issueResourcesToAllPlayers: Commands
    }

}