package com.contractsAndStates.states

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

data class RobberState(val hexTileIndex: Int, val players: List<Party>): LinearState {
    override val participants: List<AbstractParty> = players
    override val linearId = UniqueIdentifier()
}