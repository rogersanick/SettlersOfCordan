package com.contractsAndStates.states

import com.contractsAndStates.contracts.LongestRoadContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
@BelongsToContract(LongestRoadContract::class)
data class LongestRoadState(val holder: Party?,
                            override val linearId: UniqueIdentifier = UniqueIdentifier(),
                            override val participants: List<AbstractParty>) : LinearState