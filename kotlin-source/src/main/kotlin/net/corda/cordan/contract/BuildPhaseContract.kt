package net.corda.cordan.contract

import net.corda.cordan.state.GameBoardState
import net.corda.cordan.state.HexTile
import net.corda.cordan.state.SettlementState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.util.ArrayList

// ************************
// * Build Phase Contract *
// ************************

class BuildPhaseContract : Contract {
    companion object {
        const val ID = "net.corda.cordan.contract.BuildPhaseContract"
    }

    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.requireSingleCommand<BuildPhaseContract.Commands>()
        val newSettlement = tx.outputsOfType<SettlementState>().single()
        val gameBoardState = tx.inputsOfType<GameBoardState>().single()

        when (command.value) {
            is BuildPhaseContract.Commands.BuildInitialSettlement -> requireThat {

                val hexTileCoordinate = newSettlement.hexTileCoordinate

                "A settlement must not have previously been built in this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][hexTileCoordinate] )
                "A settlement must not have previously been built beside this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 0) hexTileCoordinate - 1 else 5] )
                "A settlement must not have previously been built besidez this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][if (hexTileCoordinate != 5) hexTileCoordinate + 1 else 0] )

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