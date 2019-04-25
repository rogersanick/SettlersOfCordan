package com.contractsAndStates.states

import com.contractsAndStates.contracts.GameStateContract
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.util.Collections.copy

@BelongsToContract(GameStateContract::class)
data class GameBoardState(val beginner: Boolean = false,
                          val hexTiles: MutableList<HexTile>,
                          val ports: List<Port>,
                          val players: List<Party>,
                          val turnTrackerLinearId: UniqueIdentifier,
                          val spectators: List<Party> = listOf(),
                          val settlementsPlaced: MutableList<MutableList<Boolean>> = MutableList(19) { MutableList(6) { false } },
                          var setUpComplete: Boolean = false,
                          var initialPiecesPlaced: Int = 0,
                          override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState {

    override val participants: List<Party> get() = players + spectators

}

@CordaSerializable
class HexTile(val resourceType: String,
              val roleTrigger: Int,
              val robberPresent: Boolean,
              val hexTileIndex: Int,
              var sides: MutableList<Int?> = MutableList(6) { null },
              var roads: MutableList<UniqueIdentifier?> = MutableList(6) { null }) {

    fun connect(sideIndex: Int, hexTileToConnect: HexTile) {
        if (sideIndex > 5) {
            throw Error("You have specified an invalid index.")
        }
        sides[sideIndex] = hexTileToConnect.hexTileIndex
        hexTileToConnect.sides[if (sideIndex + 3 <= 5) sideIndex + 3 else sideIndex - 3] = hexTileIndex
    }

    fun buildRoad(sideIndex: Int, roadStateLinearId: UniqueIdentifier, hexTiles: MutableList<HexTile>): MutableList<HexTile> {

        var newMutableHexTiles = hexTiles.map { it }.toMutableList()

        var newMutableListOfRoads = newMutableHexTiles[this.hexTileIndex].roads.map { it }.toMutableList()
        newMutableListOfRoads.set(sideIndex, roadStateLinearId)

        val reciprocalSideIndex = if (sideIndex + 3 > 5) sideIndex - 3 else sideIndex + 3
        if (this.sides[sideIndex] != null) {
            var newMutableReciprocalListOfRoads = newMutableHexTiles[this.sides[sideIndex]!!].roads.map { it }.toMutableList()
            newMutableReciprocalListOfRoads.set(reciprocalSideIndex, roadStateLinearId)
            newMutableHexTiles[this.sides[sideIndex]!!].roads = newMutableReciprocalListOfRoads
        }

        newMutableHexTiles[this.hexTileIndex].roads = newMutableListOfRoads
        return newMutableHexTiles
    }

}

@CordaSerializable
data class PortTile(val inputRequired: List<Amount<Resource>>, val outputRequired: List<Amount<Resource>>)

@CordaSerializable
data class Port(val portTile: PortTile, var accessPoints: List<AccessPoint>)

@CordaSerializable
data class AccessPoint(val hexTileIndex: Int, val hexTileCoordinate: List<Int>)