package net.corda.cordan.state

import net.corda.cordan.contract.BuildPhaseContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

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