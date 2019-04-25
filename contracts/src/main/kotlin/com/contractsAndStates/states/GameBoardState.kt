package com.contractsAndStates.states

import com.contractsAndStates.contracts.GameStateContract
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@BelongsToContract(GameStateContract::class)
data class GameBoardState(val beginner: Boolean = false,
                          val hexTiles: List<HexTile>,
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
              var robberPresent: Boolean,
              var hexTileIndex: Int,
              var sides: MutableList<Int?> = MutableList(6) { null },
              var roads: MutableList<UniqueIdentifier?> = MutableList(6) { null }) {

    fun connect(sideIndex: Int, hexTileToConnect: HexTile) {
        if (sideIndex > 5) {
            throw Error("You have specified an invalid index.")
        }
        sides[sideIndex] = hexTileToConnect.hexTileIndex
        hexTileToConnect.sides[if (sideIndex + 3 <= 5) sideIndex + 3 else sideIndex - 3] = hexTileIndex
    }

    fun buildRoad(sideIndex: Int, roadStateLinearId: UniqueIdentifier, gameBoardState: GameBoardState): List<HexTile> {
        this.roads[sideIndex] = roadStateLinearId
        val reciprocalSideIndex = if (sideIndex + 3 > 5) sideIndex - 3 else sideIndex + 3
        if (this.sides[sideIndex] != null) {
            gameBoardState.hexTiles[this.sides[sideIndex]!!].roads[reciprocalSideIndex] = roadStateLinearId
        }

        return gameBoardState.hexTiles
    }

}

@CordaSerializable
data class PortTile(val inputRequired: List<Amount<Resource>>, val outputRequired: List<Amount<Resource>>)

@CordaSerializable
data class Port(val portTile: PortTile, var accessPoints: List<AccessPoint>)

@CordaSerializable
data class AccessPoint(val hexTileIndex: Int, val hexTileCoordinate: List<Int>)