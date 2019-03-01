package com.oracleClient

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.states.GameBoardState
import com.oracleService.state.DiceRollState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

// ******************
// * Roll Dice Flow *
// ******************

@InitiatingFlow
@StartableByRPC
class GetRandomDiceRollValues(val turnTrackerStateLinearId: UniqueIdentifier, val gameBoardState: GameBoardState, val oracle: Party) : FlowLogic<DiceRollState>() {
    @Suspendable
    override fun call() = initiateFlow(oracle).sendAndReceive<DiceRollState>(listOf(turnTrackerStateLinearId, gameBoardState.linearId)).unwrap { it }
}