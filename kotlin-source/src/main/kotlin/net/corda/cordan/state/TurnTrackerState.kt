package net.corda.cordan.state

import net.corda.cordan.contract.TurnTrackerContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable
import java.lang.Error

@CordaSerializable
@BelongsToContract(TurnTrackerContract::class)
data class TurnTrackerState (
        val currTurnIndex: Int = 0,
        val setUpRound1Complete: Boolean = false,
        val setUpRound2Complete: Boolean = false,
        override val participants: List<AbstractParty>
): ContractState {
    fun endTurn() = copy(currTurnIndex = if (this.currTurnIndex + 1 < 4) this.currTurnIndex + 1 else 0)

    fun endTurnDuringInitialSettlementPlacement(): TurnTrackerState {
        if (setUpRound2Complete) {
            throw Error("You should be using the end turn function")
        } else if (setUpRound1Complete) {
            if (currTurnIndex == 7) return copy(currTurnIndex = currTurnIndex - 1, setUpRound2Complete = true)
            return copy(currTurnIndex = currTurnIndex - 1)
        } else {
            if (currTurnIndex == 2) return copy(currTurnIndex = currTurnIndex + 1, setUpRound1Complete = true)
            return copy(currTurnIndex = currTurnIndex + 1)
        }
    }
}