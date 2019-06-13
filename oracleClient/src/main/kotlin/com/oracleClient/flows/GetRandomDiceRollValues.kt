package com.oracleClient.flows

import co.paralleluniverse.fibers.Suspendable
import com.oracleClient.state.DiceRollState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.utilities.unwrap

// ******************
// * Roll Dice Flow *
// ******************

@InitiatingFlow(version = 1)
@StartableByRPC
class GetRandomDiceRollValues(val turnTrackerStateLinearId: UniqueIdentifier, val gameBoardStateLinearId: UniqueIdentifier, val partiesInvolved: List<Party>, val oracle: Party) : FlowLogic<DiceRollState>() {
    @Suspendable
    override fun call(): DiceRollState {
        val oracleSession = initiateFlow(oracle)
        val diceRolls = oracleSession.sendAndReceive<List<*>>(listOf(turnTrackerStateLinearId, gameBoardStateLinearId)).unwrap { it }
        val signature = oracleSession.receive<SignedDataWithCert<Party>>().unwrap { it }
        return DiceRollState(diceRolls[0] as Int, diceRolls[1] as Int, turnTrackerStateLinearId, gameBoardStateLinearId, partiesInvolved + ourIdentity, signature)
    }
}