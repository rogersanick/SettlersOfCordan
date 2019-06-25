package com.contractsAndStates.states

import com.contractsAndStates.contracts.GatherPhaseContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
@BelongsToContract(GatherPhaseContract::class)
data class RobberState(val hexTileIndex: Int, val players: List<Party>): LinearState {
    override val participants: List<AbstractParty> = players
    override val linearId = UniqueIdentifier()

    fun move(hexTileIndex: Int) = copy(hexTileIndex = hexTileIndex)
}