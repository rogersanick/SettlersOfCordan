package com.contractsAndStates.states

import com.contractsAndStates.contracts.GameStateContract
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.util.Collections.copy

/**
 * This state represents the same shared data that the symbolic representation of the Settlers board game
 * and game-pieces represent in real life.
 *
 * It contains an ordered list of the hexTiles that were used in its construction, the locations and
 * specifications of individual ports, and other pieces of data that act as infrastructure and validation for
 * future transactions. It is frequently used as a reference state (for example, in the issueResourcesFlow)
 */

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
                          val winner: Party,
                          override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState {

    override val participants: List<Party> get() = players

    fun weWin(ourIdentity: Party): GameBoardState = copy(winner = ourIdentity)

}

@CordaSerializable
class HexTile(val resourceType: String,
              val roleTrigger: Int,
              val robberPresent: Boolean,
              val hexTileIndex: Int,
              var sides: MutableList<Int?> = MutableList(6) { null },
              var roads: MutableList<UniqueIdentifier?> = MutableList(6) { null }) {

    /**
     * This method is used to create a fully connected graph of HexTiles. This enables some
     * funky maths that we will use later on to calculate the validity of transactions.
     */

    fun connect(sideIndex: Int, hexTileToConnect: HexTile) {
        if (sideIndex > 5) {
            throw Error("You have specified an invalid index.")
        }
        sides[sideIndex] = hexTileToConnect.hexTileIndex
        hexTileToConnect.sides[if (sideIndex + 3 <= 5) sideIndex + 3 else sideIndex - 3] = hexTileIndex
    }

    /**
     * This method is used in flows to product a new version of the gameboard with a record of the location of roads, identified by
     * their specific linearID
     *
     * TODO: Add functionality to connect roadStates when new roads and proposed extending existing roads.
     */

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