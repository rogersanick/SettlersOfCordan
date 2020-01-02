package com.r3.cordan.primary.contracts.robber

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

// *************************
// * Turn Tracker Contract *
// *************************

class PlayBlockerContract : Contract {

    companion object {
        const val ID = "com.r3.cordan.primary.contracts.robber.PlayBlockerContract"
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()

        when (command.value) {

            is Commands.RemovePlayBlocker -> {}

        }
    }

    interface Commands : CommandData {
        class IssuePlayBlockers: Commands
        class RemovePlayBlocker: Commands
    }
}