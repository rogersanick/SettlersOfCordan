package com.contractsAndStates.states

import com.contractsAndStates.contracts.GatherPhaseContract
import com.contractsAndStates.contracts.RobberContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
@BelongsToContract(RobberContract::class)
data class RobberState(val hexTileIndex: Int,
                       val players: List<Party>,
                       override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState {
    override val participants: List<AbstractParty> = players


    fun move(hexTileIndex: Int) = copy(hexTileIndex = hexTileIndex)
}