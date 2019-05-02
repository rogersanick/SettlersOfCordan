package com.contractsAndStates.states

import com.contractsAndStates.contracts.BuildPhaseContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * Settlements are the fundamental structure one may build in Settlers of Cordan. They
 * give the owner the right to claim (self-issue) resources of a certain type based on
 * where the settlement was built. This issuance is based on the random dice roll a player
 * receives from the dice roll and uses to propose the outcome of the next Gather phase.
 */

@BelongsToContract(BuildPhaseContract::class)
data class SettlementState(
        val hexTileIndex: Int,
        val hexTileCoordinate: Int,
        val players: List<Party>,
        val owner: Party,
        val resourceAmountClaim: Int = 1,
        val upgradedToCity: Boolean = false
): ContractState {
    fun upgradeToCity() = copy(resourceAmountClaim = 2, upgradedToCity = true)
    override val participants: List<AbstractParty> get() = players
}