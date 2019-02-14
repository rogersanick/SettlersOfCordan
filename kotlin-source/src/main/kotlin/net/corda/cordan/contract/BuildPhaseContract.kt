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
                "A settlement must not have previously been built in this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][hexTileCoordinate + 1] )
                "A settlement must not have previously been built in this location." using ( !gameBoardState.settlementsPlaced[newSettlement.hexTileIndex][hexTileCoordinate - 1] )

                class LinkedListNode(val int: Int, var next: LinkedListNode? = null)
                val linkedListNode1 = LinkedListNode(1)
                val linkedListNode2 = LinkedListNode(3)
                linkedListNode1.next = linkedListNode2
                val linkedListNode3 = LinkedListNode(5)
                linkedListNode2.next = linkedListNode3
                linkedListNode3.next = linkedListNode1

                val linkedList2Node1 = LinkedListNode(0)
                val linkedList2Node2 = LinkedListNode(2)
                linkedList2Node1.next = linkedList2Node2
                val linkedList2Node3 = LinkedListNode(4)
                linkedList2Node2.next = linkedList2Node3
                linkedList2Node3.next = linkedList2Node1

                var linkedListToGetCoordinateOfPotentiallyConflictingSettlement: LinkedListNode?

                if (hexTileCoordinate % 2 == 0) {
                    linkedListToGetCoordinateOfPotentiallyConflictingSettlement = linkedListNode1
                    while (hexTileCoordinate != linkedListToGetCoordinateOfPotentiallyConflictingSettlement?.int) {
                        linkedListToGetCoordinateOfPotentiallyConflictingSettlement = linkedListToGetCoordinateOfPotentiallyConflictingSettlement?.next
                    }
                } else {
                    linkedListToGetCoordinateOfPotentiallyConflictingSettlement = linkedListNode2
                    while (hexTileCoordinate != linkedListToGetCoordinateOfPotentiallyConflictingSettlement?.int) {
                        linkedListToGetCoordinateOfPotentiallyConflictingSettlement = linkedListToGetCoordinateOfPotentiallyConflictingSettlement?.next
                    }
                }

                val coordinateOfPotentiallyConflictingSettlement1 = linkedListToGetCoordinateOfPotentiallyConflictingSettlement.next?.int!!
                val coordinateOfPotentiallyConflictingSettlement2 = linkedListToGetCoordinateOfPotentiallyConflictingSettlement.next?.next?.int!!

                val indexOfHexTile = newSettlement.hexTileIndex
                val relevantHexTileNeighbours: ArrayList<HexTile?> = arrayListOf()

                if (hexTileCoordinate != 5) {
                    relevantHexTileNeighbours.add(gameBoardState.hexTiles[indexOfHexTile].sides[hexTileCoordinate])
                    relevantHexTileNeighbours.add(gameBoardState.hexTiles[indexOfHexTile].sides[hexTileCoordinate])
                } else {
                    relevantHexTileNeighbours.add(gameBoardState.hexTiles[indexOfHexTile].sides[hexTileCoordinate])
                    relevantHexTileNeighbours.add(gameBoardState.hexTiles[indexOfHexTile].sides[hexTileCoordinate])
                }

                val indexOfRelevantHexTileNeighbour1 = gameBoardState.hexTiles.indexOf(relevantHexTileNeighbours[0])
                val indexOfRelevantHexTileNeighbour2 = gameBoardState.hexTiles.indexOf(relevantHexTileNeighbours[1])

                "There must not be a settlement already built within hexSide length of the existing settlement" using (indexOfRelevantHexTileNeighbour1 == -1 || gameBoardState.settlementsPlaced[indexOfRelevantHexTileNeighbour1][coordinateOfPotentiallyConflictingSettlement1])
                "There must not be a settlement already built where the new settlement is to be built" using (indexOfRelevantHexTileNeighbour1 == -1 || gameBoardState.settlementsPlaced[indexOfRelevantHexTileNeighbour1][coordinateOfPotentiallyConflictingSettlement1 + 1])
                "There must not be a settlement already built where the new settlement is to be built" using (indexOfRelevantHexTileNeighbour1 == -1 || gameBoardState.settlementsPlaced[indexOfRelevantHexTileNeighbour1][coordinateOfPotentiallyConflictingSettlement1 - 1])


                "There must not be a settlement already built within hexSide length of the existing settlement" using (indexOfRelevantHexTileNeighbour2 == -1 || gameBoardState.settlementsPlaced[indexOfRelevantHexTileNeighbour2][coordinateOfPotentiallyConflictingSettlement2])
                "There must not be a settlement already built where the new settlement is to be built" using (indexOfRelevantHexTileNeighbour2 == -1 || gameBoardState.settlementsPlaced[indexOfRelevantHexTileNeighbour2][coordinateOfPotentiallyConflictingSettlement2 + 1])
                "There must not be a settlement already built where the new settlement is to be built" using (indexOfRelevantHexTileNeighbour2 == -1 || gameBoardState.settlementsPlaced[indexOfRelevantHexTileNeighbour2][coordinateOfPotentiallyConflictingSettlement2 - 1])

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