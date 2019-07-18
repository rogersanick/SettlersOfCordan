package com.contractsAndStates.states

import com.contractsAndStates.contracts.BuildPhaseContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

/**
 * Roads are pieces of infrastructure that connect your settlements and cities.
 * Players are unable to build new settlements without first building new roads to connect
 * them with existing settlements (at least two paths away).
 *
 * Only one road may be built on each path - this is verified by the Corda contract.
 * You may build roads along the coast.
 */

@CordaSerializable
@BelongsToContract(BuildPhaseContract::class)
data class RoadState(
        val hexTileIndex: HexTileIndex,
        val hexTileSide: TileSideIndex,
        val players: List<Party>,
        val owner: Party,
        val roadAttachedA: UniqueIdentifier? = null,
        val roadAttachedB: UniqueIdentifier? = null,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {
    override val participants: List<AbstractParty> = players

    fun getAbsoluteSide() = AbsoluteSide(hexTileIndex, hexTileSide)

    /**
     * In Settlers of Catan, players earn additional victory points for maintaining the longest
     * road - so long as that continuous road is comprised of 5 or more adjacent roads. The methods
     * below are helpers to enable our keeping track of which roads might be the longest.
     */

    fun attachRoadA(linearIdentifier: UniqueIdentifier): RoadState {
        return this.copy(roadAttachedA = linearIdentifier)
    }

    fun attachRoadB(linearIdentifier: UniqueIdentifier): RoadState {
        return this.copy(roadAttachedB = linearIdentifier)
    }
}

