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
    }

    fun getRollTrigger() = RollTrigger(randomRoll1 + randomRoll2)
}

@CordaSerializable
data class RollTrigger(val real: Boolean, val total: Int) {

    constructor() : this(false, 0)
    constructor(total: Int) : this(true, total)

    init {
        require(real || total == 0) {
            "Not real one should have a total of 0"
        }
        require(!real || 2 * DiceRollState.MIN_ROLL <= total) {
            "A real one's total has to larger than ${DiceRollState.MIN_ROLL * 2}"
        }
        require(!real || total <= 2 * DiceRollState.MAX_ROLL) {
            "A real one's total has to smaller than ${DiceRollState.MAX_ROLL * 2}"
        }
        require(!real || total != 7) {
            "A real one's total cannot be 7"
        }
    }
}