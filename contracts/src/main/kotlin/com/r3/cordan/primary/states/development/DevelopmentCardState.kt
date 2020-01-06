package com.r3.cordan.primary.states.development

import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

class DevelopmentCardState(
        val owner: Party,
        val cardType: DevelopmentCardType
): ContractState {
    override val participants: List<AbstractParty> = listOf(owner)
}

enum class DevelopmentCardType {
    YEAR_OF_PLENTY,
    MONOPOLY,
    KNIGHT,
    ROAD_BUILDING,
    VICTORY_POINT
}