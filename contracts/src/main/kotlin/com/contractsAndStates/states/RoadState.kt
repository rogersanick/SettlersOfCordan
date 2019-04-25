package com.contractsAndStates.states

import com.contractsAndStates.contracts.BuildPhaseContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

@BelongsToContract(BuildPhaseContract::class)
data class RoadState (
        val hexTileIndex: Int,
        val hexTileSide: Int,
        val players: List<Party>,
        val owner: Party,
        val roadAttachedA: UniqueIdentifier? = null,
        val roadAttachedB: UniqueIdentifier? = null,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
): ContractState, LinearState {

    override val participants: List<AbstractParty> get() = players

    fun attachRoadA(linearIdentifier: UniqueIdentifier): RoadState {
        return this.copy(roadAttachedA = linearIdentifier)
    }

    fun attachRoadB(linearIdentifier: UniqueIdentifier): RoadState {
        return this.copy(roadAttachedB = linearIdentifier)
    }
}

