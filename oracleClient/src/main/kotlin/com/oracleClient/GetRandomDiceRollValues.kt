package com.oracleClient

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.states.GameBoardState
import com.oracleService.state.DiceRollState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.SignedDataWithCert
import net.corda.core.utilities.unwrap

// ******************
// * Roll Dice Flow *
// ******************

@InitiatingFlow
@StartableByRPC
class GetRandomDiceRollValues(val turnTrackerStateLinearId: UniqueIdentifier, val gameBoardState: GameBoardState, val oracle: Party) : FlowLogic<DiceRollState>() {
    @Suspendable
    override fun call(): DiceRollState {
        val oracleSession = initiateFlow(oracle)
        val diceRolls = oracleSession.sendAndReceive<List<*>>(listOf(turnTrackerStateLinearId, gameBoardState.linearId)).unwrap { it }
        val signature = oracleSession.receive<SecureHash>().unwrap { it }
        return DiceRollState(diceRolls[0] as Int, diceRolls[1] as Int, turnTrackerStateLinearId, gameBoardState.linearId, gameBoardState.participants + oracle, signature )
    }
}