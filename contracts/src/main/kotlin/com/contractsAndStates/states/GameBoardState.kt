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
              var sides: MutableList<Int?> = MutableList(6) { null }) {
    fun connect(index: Int, hexTileToConnect: HexTile) {
        if (index > 5) {
            throw Error("You have specified an invalid index.")
        }
        sides[index] = hexTileToConnect.hexTileIndex
        hexTileToConnect.sides[if (index + 3 <= 5) index + 3 else index - 3] = hexTileIndex
    }
}

@CordaSerializable
data class PortTile(val inputRequired: List<Amount<Resource>>, val outputRequired: List<Amount<Resource>>)

@CordaSerializable
data class Port(val portTile: PortTile, var accessPoints: List<AccessPoint>)

@CordaSerializable
data class AccessPoint(val hexTileIndex: Int, val hexTileCoordinate: List<Int>)