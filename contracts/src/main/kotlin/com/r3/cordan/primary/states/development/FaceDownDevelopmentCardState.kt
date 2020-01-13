package com.r3.cordan.primary.states.development

import com.r3.cordan.primary.contracts.development.DevelopmentCardContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
@BelongsToContract(DevelopmentCardContract::class)
class FaceDownDevelopmentCardState(
        val owner: Party,
        val players: List<Party>,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
): ContractState, LinearState {
    override val participants: List<AbstractParty> = players
}