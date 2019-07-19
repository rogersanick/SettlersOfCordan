package com.oracleClientStatesAndContracts.states

import com.oracleClientStatesAndContracts.contracts.DiceRollContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
@BelongsToContract(DiceRollContract::class)
data class DiceRollState(
        val randomRoll1: Int,
        val randomRoll2: Int,
        val turnTrackerUniqueIdentifier: UniqueIdentifier,
        val gameBoardStateUniqueIdentifier: UniqueIdentifier,
        override val participants: List<Party>,
        val signedDataWithOracleCert: SignedDataWithCert<Party>
) : ContractState, PersistentState() {

    constructor(diceRollState: DiceRollState) : this(
            diceRollState.randomRoll1,
            diceRollState.randomRoll2,
            diceRollState.turnTrackerUniqueIdentifier,
            diceRollState.gameBoardStateUniqueIdentifier,
            diceRollState.participants,
            diceRollState.signedDataWithOracleCert
    )

    init {
        require(MIN_ROLL <= randomRoll1) {
            "Roll has to be >= $MIN_ROLL"
        }
        require(randomRoll1 <= MAX_ROLL) {
            "Roll has to <= $MAX_ROLL"
        }
    }

    companion object {
        const val MIN_ROLL = 1
        const val MAX_ROLL = 6
        const val TOTAL_ROBBER = 7
    }

    fun getRollTotal() = randomRoll1 + randomRoll2
    fun isRobberTotal() = getRollTotal() == TOTAL_ROBBER
    fun getRollTrigger() = RollTrigger(getRollTotal())
}

@CordaSerializable
data class RollTrigger(val total: Int) {

    init {
        require(2 * DiceRollState.MIN_ROLL <= total) {
            "A real one's total has to larger than ${DiceRollState.MIN_ROLL * 2}"
        }
        require(total <= 2 * DiceRollState.MAX_ROLL) {
            "A real one's total has to smaller than ${DiceRollState.MAX_ROLL * 2}"
        }
        require(total != DiceRollState.TOTAL_ROBBER) {
            "A real one's total cannot be ${DiceRollState.TOTAL_ROBBER}"
        }
    }
}