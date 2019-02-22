package com.contractsAndStates.states

import com.contractsAndStates.contracts.TurnTrackerContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable
import java.lang.Error

@CordaSerializable
@BelongsToContract(TurnTrackerContract::class)
data class TurnTrackerState (
        val currTurnIndex: Int = 0,
        val setUpRound1Complete: Boolean = false,
        val setUpRound2Complete: Boolean = false,
        override val participants: List<AbstractParty>,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
): ContractState, LinearState {
    fun endTurn() = copy(currTurnIndex = if (this.currTurnIndex + 1 < 4) this.currTurnIndex + 1 else 0, linearId = linearId)

    fun endTurnDuringInitialSettlementPlacement(): TurnTrackerState {
        if (setUpRound2Complete) {
            throw Error("You should be using the end turn function")
        } else if (setUpRound1Complete) {
            if (currTurnIndex == 0) return copy(currTurnIndex = currTurnIndex, setUpRound2Complete = true, linearId = linearId)
            return copy(currTurnIndex = currTurnIndex - 1, linearId = linearId)
        } else {
            if (currTurnIndex == 3) return copy(currTurnIndex = currTurnIndex, setUpRound1Complete = true)
            return copy(currTurnIndex = currTurnIndex + 1, linearId = linearId)
        }
    }
}