package com.contractsAndStates.states

import com.contractsAndStates.contracts.BuildPhaseContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.CordaSerializable

/**
 * Settlements are the fundamental structure one may build in Settlers of Cordan. They
 * give the owner the right to claim (self-issue) resources of a certain type based on
 * where the settlement was built. This issuance is based on the random dice roll a player
 * receives from the dice roll and uses to propose the outcome of the next Gather phase.
 */

@CordaSerializable
@BelongsToContract(BuildPhaseContract::class)
data class SettlementState @ConstructorForDeserialization constructor(
        val absoluteCorner: AbsoluteCorner,
        val players: List<Party>,
        val owner: Party,
        val resourceAmountClaim: Int = settlementAmountClaim
) : ContractState {

    constructor(
            hexTileIndex: HexTileIndex,
            hexTileCoordinate: TileCornerIndex,
            players: List<Party>,
            owner: Party,
            resourceAmountClaim: Int = settlementAmountClaim
    ) : this(
            absoluteCorner = AbsoluteCorner(hexTileIndex, hexTileCoordinate),
            players = players,
            owner = owner,
            resourceAmountClaim = resourceAmountClaim)

    val upgradedToCity = resourceAmountClaim == cityAmountClaim

    init {
        require(resourceAmountClaim in listOf(settlementAmountClaim, cityAmountClaim)) {
            "resourceAmountClaim of $resourceAmountClaim is not an allowed value"
        }
    }

    companion object {
        const val settlementAmountClaim = 1
        const val cityAmountClaim = 2
    }

    fun upgradeToCity() = copy(resourceAmountClaim = cityAmountClaim)
    override val participants: List<AbstractParty> get() = players
}