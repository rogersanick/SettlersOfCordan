package com.r3.cordan.primary.states.robber

import com.r3.cordan.primary.contracts.robber.PlayBlockerContract
import com.r3.cordan.primary.states.resources.Resource
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
@BelongsToContract(PlayBlockerContract::class)
data class PlayBlockerState(val playerBlocked: Party,
                            val players: List<Party>,
                            val price: Int,
                            val reason: BlockedStatus,
                            val gameBoardLinearId: UniqueIdentifier,
                            val resourceSpecificPrice: Map<Resource, Int>? = null,
                            override val linearId: UniqueIdentifier = UniqueIdentifier()
                            ): ContractState, LinearState {
    override val participants: List<AbstractParty> = players
}

@CordaSerializable
enum class BlockedStatus {
    MUST_PAY_RESOURCES_TO_PLAYER,
    MUST_DISCARD_RESOURCES,
    MUST_PAY_ROBBER_STOLEN_RESOURCE
}