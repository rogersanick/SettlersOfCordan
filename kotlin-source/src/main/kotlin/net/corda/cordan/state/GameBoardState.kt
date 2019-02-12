package net.corda.cordan.state

import net.corda.cordan.contract.GameStateContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@BelongsToContract(GameStateContract::class)
data class GameBoardState(val beginner: Boolean = false,
                          val hexTiles: List<HexTile>,
                          val ports: List<Port>,
                          val players: List<Party>,
                          val spectators: List<Party> = listOf(),
                          val settlementsPlaced: MutableList<MutableList<Boolean>> = MutableList(18) { MutableList(6) { false } },
                          var setUpComplete: Boolean = false,
                          var initialPiecesPlaced: Int = 0,
                          override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState {

    override val participants: List<Party> get() = players + spectators

}

@CordaSerializable
data class HexTile(val resourceType: String,
                   val roleTrigger: Int,
                   var robberPresent: Boolean,
                   var sides: MutableList<HexTile?> = MutableList(6) { null }) {
    fun connect(index: Int, hexTileToConnect: HexTile) {
        if (index > 5) {
            throw Error("You have specified an invalid index.")
        }
        sides[index] = hexTileToConnect
        hexTileToConnect.sides[if (index + 3 <= 5) index + 3 else index - 3]
    }
}

@CordaSerializable
data class PortTile(val amountInputRequired: Amount<GameCurrency>)

@CordaSerializable
data class Port(val portTile: PortTile, var accessPoints: List<AccessPoint>)

@CordaSerializable
data class AccessPoint(val hexTileIndex: Int, val hexTileCoordinate: List<Int>)