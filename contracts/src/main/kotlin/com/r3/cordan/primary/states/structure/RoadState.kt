package com.r3.cordan.primary.states.structure

import com.r3.cordan.primary.contracts.structure.BuildPhaseContract
import com.r3.cordan.primary.states.board.*
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.schemas.StatePersistable
import net.corda.core.serialization.CordaSerializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

/**
 * Roads are pieces of infrastructure that connect your settlements and cities.
 * Players are unable to build new settlements without first building new roads to connect
 * them with existing settlements (at least two paths away).
 *
 * Only one road may be built on each path - this is verified by the Corda contract.
 * You may build roads along the coast.
 */

@CordaSerializable
@BelongsToContract(BuildPhaseContract::class)
data class RoadState(
        override val gameBoardLinearId: UniqueIdentifier,
        val absoluteSide: AbsoluteSide,
        val players: List<Party>,
        val owner: Party,
        val roadAttachedA: UniqueIdentifier? = null,
        val roadAttachedB: UniqueIdentifier? = null,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState, QueryableState, StatePersistable, HasGameBoardId {
    override val participants: List<AbstractParty> = players

    /**
     * In Settlers of Catan, players earn additional victory points for maintaining the longest
     * road - so long as that continuous road is comprised of 5 or more adjacent roads. The methods
     * below are helpers to enable our keeping track of which roads might be the longest.
     */

    fun attachRoadA(linearIdentifier: UniqueIdentifier): RoadState {
        return this.copy(roadAttachedA = linearIdentifier)
    }

    fun attachRoadB(linearIdentifier: UniqueIdentifier): RoadState {
        return this.copy(roadAttachedB = linearIdentifier)
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is RoadSchemaV1 -> RoadSchemaV1.PersistentRoadState(
                    gameBoardLinearId.toString(),
                    absoluteSide.tileIndex.value,
                    absoluteSide.sideIndex.value,
                    owner,
                    linearId.toString())
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> {
        return listOf(RoadSchemaV1)
    }
}

object RoadSchema

@CordaSerializable
object RoadSchemaV1 : MappedSchema(
        schemaFamily = RoadSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentRoadState::class.java)
) {
    @Entity
    @Table(
            name = "contract_road_states",
            indexes = [
                Index(name = "${BelongsToGameBoard.columnName}_idx", columnList = BelongsToGameBoard.columnName),
                Index(name = "tile_index_idx", columnList = "tile_index"),
                Index(name = "side_index_idx", columnList = "side_index"),
                Index(name = "owner_idx", columnList = "owner")
            ])
    class PersistentRoadState(
            @Column(name = BelongsToGameBoard.columnName, nullable = false)
            override var gameBoardLinearId: String,

            @Column(name = "tile_index", nullable = false)
            var tileIndex: Int,

            @Column(name = "side_index", nullable = false)
            var sideIndex: Int,

            @Column(name = "owner", nullable = false)
            var owner: Party,

            @Column(name = "linear_id", nullable = false)
            var linearId: String
    ) : PersistentState(), StatePersistable, HasGameBoardIdSchema
}

/**
 * Returns the player who should received the Longest Road Card given the current state
 * of the board, roads, settlements, players and the current holder of the card. Either the
 * current holder and the return value can be null meaning no player had or will have the card.
 */
fun longestRoad(board: PlacedHexTiles,
                roads: List<RoadState>,
                settlements: List<SettlementState>,
                players: List<Party>,
                currentHolder: Party?): Party? {
    val candidates = longestRoadsForAllPlayers(board, roads, settlements, players)
    return assignLongestRoad(currentHolder, candidates)
}

data class LongestRoadCandidate(val player: Party, val longestRoadLength: Int)

/**
 * Given a list of pairs of parties and count of the longest road and the current player with
 * the longest road card (null means no one has the card), return the new card holder. It can
 * be a new player, the same player or null if no one meets the criteria.
 */
fun assignLongestRoad(holder: Party?, candidates: List<LongestRoadCandidate>): Party? {

    val orderedCandidates = candidates.sortedByDescending { it.longestRoadLength }

    // Longest candidate does not have at least 5 roads
    if (orderedCandidates[0].longestRoadLength < 5)
        return null

    // Only one player has the longest road
    if (orderedCandidates[0].longestRoadLength > orderedCandidates[1].longestRoadLength)
        return orderedCandidates[0].player

    val holderLongestRoadLength = orderedCandidates.first { it.player == holder }.longestRoadLength

    // Holder and another player has same length
    if (holderLongestRoadLength == orderedCandidates[0].longestRoadLength)
        return holder

    // All other cases. More than one player has same length and longer than current holder
    return null
}

/**
 * Returns a list with the count of the longest road for each player.
 */
fun longestRoadsForAllPlayers(board: PlacedHexTiles,
                              roads: List<RoadState>,
                              settlements: List<SettlementState>,
                              players: List<Party>): List<LongestRoadCandidate> {
    return players.map { LongestRoadCandidate(it, longestRoadForPlayer(board, roads, settlements, it).count()) }
}

/**
 * Returns a set of road ids with the longest road for a particular player.
 */
fun longestRoadForPlayer(board: PlacedHexTiles,
                         roads: List<RoadState>,
                         settlements: List<SettlementState>,
                         player: Party): Set<UniqueIdentifier> {
    val playerRoadIds = roads.filter { it.owner == player }.toSet()
    val othersSettlements = settlements.filter { it.owner != player }.toSet()
    return calculateLongestRoad(board, playerRoadIds, othersSettlements)
}

/**
 * Calculate the longest road for a player.
 * roads must only contain roads for the player.
 * settlements must only contain settlements of all the other players.
 */
private fun calculateLongestRoad(board: PlacedHexTiles,
                                 roads: Set<RoadState>,
                                 settlements: Set<SettlementState>): Set<UniqueIdentifier> {
    val roadIds = roads.map { it.linearId }.toSet()
    val settlementsCorners = settlements.map { it.absoluteCorner }.toSet()

    var longestRoad = setOf<UniqueIdentifier>()
    val neverVisitedRoads = roads.map { it.linearId }.toMutableSet()

    while (neverVisitedRoads.isNotEmpty()) {
        val firstNeverVisitedRoad = roads.first { it.linearId == neverVisitedRoads.first() }
        val candidate = calculateLongestRoad(
                board, firstNeverVisitedRoad, roadIds, settlementsCorners)

        if (candidate.count() > longestRoad.count())
            longestRoad = candidate

        neverVisitedRoads -= candidate
    }

    return longestRoad
}

private fun calculateLongestRoad(board: PlacedHexTiles,
                                 roadState: RoadState,
                                 playerRoads: Set<UniqueIdentifier>,
                                 otherPlayersSettlements: Set<AbsoluteCorner>): Set<UniqueIdentifier> {
    val startFromSide = roadState.absoluteSide
    val startFromId = roadState.linearId
    val absCorners = startFromSide.sideIndex
            .getAdjacentCorners()
            .map { AbsoluteCorner(startFromSide.tileIndex, it) }

    val visited = calculateLongestRoad(board, startFromSide, absCorners.first(), setOf(), playerRoads, otherPlayersSettlements)

    return calculateLongestRoad(board, startFromSide, absCorners.last(), visited - startFromId,
            playerRoads - visited + startFromId, otherPlayersSettlements)
}

private fun calculateLongestRoad(board: PlacedHexTiles,
                                 candidate: AbsoluteSide,
                                 lastVisitedCorner: AbsoluteCorner,
                                 visited: Set<UniqueIdentifier>,
                                 available: Set<UniqueIdentifier>,
                                 otherPlayersSettlements: Set<AbsoluteCorner>): Set<UniqueIdentifier> {
    val roadId = board.get(candidate.tileIndex).sides.getRoadOn(candidate.sideIndex)

    // No road built
    if (roadId == null) return visited

    // Road from a different user
    if (!available.contains(roadId)) return visited

    // Road visited already
    if (visited.contains(roadId)) return visited

    // New visited list
    val longestRoadVisited = visited + roadId

    // Calculate next corner to expand
    val nextCornerIndex = candidate.sideIndex.getAdjacentCorners() - lastVisitedCorner.cornerIndex
    val nextCorner = AbsoluteCorner(candidate.tileIndex, nextCornerIndex.single())

    // List with the corner and overlapped corners
    val overlappedCorners = board.getOverlappedCorners(nextCorner).filterNotNull() + nextCorner

    // If settlement from other player do not expand corner
    if (otherPlayersSettlements.any { overlappedCorners.contains(it) }) {
        return longestRoadVisited
    }

    val candidates = overlappedCorners.flatMap { absCorner ->
        absCorner.cornerIndex.getAdjacentSides().map { side -> Pair(absCorner, AbsoluteSide(absCorner.tileIndex, side)) }
    }

    val visitedFromCandidates = candidates.map {
        calculateLongestRoad(board, it.second, it.first, longestRoadVisited, available - longestRoadVisited, otherPlayersSettlements)
    }

    return visitedFromCandidates.maxBy { it.count() }!!.toSet()
}
