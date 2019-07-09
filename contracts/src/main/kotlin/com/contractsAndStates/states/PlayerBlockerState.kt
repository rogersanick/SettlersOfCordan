package com.contractsAndStates.states

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class PlayerBlockerState(val playerBlocked: Party,
                              val players: List<Party>,
                              val price: Map<Resource, Amount<Resource>>): ContractState {
    override val participants: List<AbstractParty> = players
}