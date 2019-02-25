package com.oracleService.state

import com.contractsAndStates.states.GameBoardState
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.PersistentState
import net.corda.core.serialization.CordaSerializable
import java.util.*

@CordaSerializable
data class DiceRollState(
        val randomRoll1: Int,
        val randomRoll2: Int,
        val turnTrackerUniqueIdentifier: UniqueIdentifier,
        val gameBoardStateUniqueIdentifier: UniqueIdentifier,
        override val participants: List<Party>
): ContractState, PersistentState()