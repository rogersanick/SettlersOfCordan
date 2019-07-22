package com.contractsAndStates.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

// *************************
// * Gather Phase Contract *
// *************************

class TradePhaseContract : Contract {
    companion object {
        const val ID = "com.contractsAndStates.contracts.TradePhaseContract"
    }

    override fun verify(tx: LedgerTransaction) {
    }

    interface Commands : CommandData {
        class TradeWithPort: Commands
        class IssueTrade: Commands
        class CancelTrade: Commands
        class ExecuteTrade: Commands
    }
}