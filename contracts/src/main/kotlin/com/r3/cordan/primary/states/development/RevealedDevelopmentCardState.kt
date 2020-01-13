package com.r3.cordan.primary.states.development

import com.r3.cordan.primary.contracts.development.DevelopmentCardContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
@BelongsToContract(DevelopmentCardContract::class)
class RevealedDevelopmentCardState(
        val owner: Party,
        val cardType: DevelopmentCardType,
        val faceDownDevelopmentCardId: UniqueIdentifier
): ContractState {
    override val participants: List<AbstractParty> = listOf(owner)
}

@CordaSerializable
enum class DevelopmentCardType {
    YEAR_OF_PLENTY,
    MONOPOLY,
    KNIGHT,
    ROAD_BUILDING,
    VICTORY_POINT
}