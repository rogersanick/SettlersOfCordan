package com.contractsAndStates.contracts

import com.contractsAndStates.states.BlockedStatus
import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.RobberState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

// *************************
// * Turn Tracker Contract *
// *************************

class PlayBlockerContract : Contract {

    companion object {
        const val ID = "com.contractsAndStates.contracts.PlayBlockerContract"
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