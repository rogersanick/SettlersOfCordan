package com.contractsAndStates.contracts

import com.contractsAndStates.states.GameBoardState
import com.contractsAndStates.states.Resource
import com.contractsAndStates.states.SettlementState
import com.contractsAndStates.states.TurnTrackerState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.internal.sumByLong
import net.corda.core.transactions.LedgerTransaction
import net.corda.sdk.token.contracts.states.OwnedTokenAmount
import java.util.ArrayList

// ************************
// * Build Phase Contract *
// ************************

class BuildPhaseContract : Contract {
    companion object {
        const val ID = "com.contractsAndStates.contracts.BuildPhaseContract"
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<Commands>()
        val newSettlement = tx.outputsOfType<SettlementState>().single()
        val resources = tx.inputsOfType<OwnedTokenAmount<*>>()
        val wheatInTx = resources.filter { it.amount.token.product == Resource.getInstance("Field") }.sumByLong { it.amount.quantity }
        val brickInTx = resources.filter { it.amount.token.product == Resource.getInstance("Hill") }.sumByLong { it.amount.quantity }
        val sheepInTx = resources.filter { it.amount.token.product == Resource.getInstance("Pasture") }.sumByLong { it.amount.quantity }
        val woodInTx = resources.filter { it.amount.token.product == Resource.getInstance("Forest") }.sumByLong { it.amount.quantity }
        val referenceTurnTracker = tx.referenceInputRefsOfType<TurnTrackerState>().single().state.data

                when (command.value) {

            is Commands.BuildInitialSettlement -> requireThat {
                val gameBoardState = tx.inputsOfType<GameBoardState>().single()
                val hexTileCoordinate = newSettlement.hexTileCoordinate

                if (referenceTurnTracker.setUpRound1Complete) {
                    "The player should be issuing them self a resource of the appropriate type" using ( Resource.getInstance(gameBoardState.hexTiles[newSettlement.hexTileIndex].resourceType) == tx.outputsOfType<OwnedTokenAmount<*>>().single().amount.token.product)
                }

                "A settlement must not have previously been built in this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][hexTileCoordinate] )
                "A settlement must not have previously been built beside this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 0) hexTileCoordinate - 1 else 5] )
                "A settlement must not have previously been built beside this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 5) hexTileCoordinate + 1 else 0] )

            }

            is Commands.BuildInitialSettlement -> requireThat {
                val gameBoardState = tx.referenceInputsOfType<GameBoardState>().single()
                val hexTileCoordinate = newSettlement.hexTileCoordinate

                "A settlement must not have previously been built in this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][hexTileCoordinate] )
                "A settlement must not have previously been built beside this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 0) hexTileCoordinate - 1 else 5] )
                "A settlement must not have previously been built beside this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 5) hexTileCoordinate + 1 else 0] )

                "The player must have provided the appropriate amount of wheat to build a settlement" using ( wheatInTx == 1.toLong())
                "The player must have provided the appropriate amount of brick to build a settlement" using ( brickInTx == 1.toLong())
                "The player must have provided the appropriate amount of ore to build a settlement" using ( sheepInTx == 1.toLong())
                "The player must have provided the appropriate amount of  to build a settlement" using ( woodInTx == 1.toLong())
                "There must be no input settlements" using (tx.inputsOfType<SettlementState>().size == 1)
                "The player must be attempting to build a single settlement" using (tx.outputsOfType<SettlementState>().size == 1)
            }
        }

    }

    interface Commands : CommandData {
        class BuildInitialSettlement: Commands
        class BuildSettlement: Commands
        class BuildCity: Commands
        class BuildRoad: Commands
    }

}