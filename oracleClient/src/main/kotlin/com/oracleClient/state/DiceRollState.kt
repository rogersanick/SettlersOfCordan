package com.oracleClient.state

import com.oracleClient.contracts.DiceRollContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable


fun DiceRollState(diceRollState: DiceRollState) = DiceRollState(
        diceRollState.randomRoll1,
        diceRollState.randomRoll2,
        diceRollState.turnTrackerUniqueIdentifier,
        diceRollState.gameBoardStateUniqueIdentifier,
        diceRollState.participants,
        diceRollState.signedDataWithOracleCert
)

@CordaSerializable
@BelongsToContract(DiceRollContract::class)
data class DiceRollState(
        val randomRoll1: Int,
        val randomRoll2: Int,
        val turnTrackerUniqueIdentifier: UniqueIdentifier,
        val gameBoardStateUniqueIdentifier: UniqueIdentifier,
        override val participants: List<Party>,
        val signedDataWithOracleCert: SecureHash
): ContractState, PersistentState()