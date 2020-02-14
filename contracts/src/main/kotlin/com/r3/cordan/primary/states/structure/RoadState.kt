package com.r3.cordan.primary.states.structure

import co.paralleluniverse.fibers.Suspendable
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
import java.util.*
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
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState, QueryableState, StatePersistable, HasGameBoardId {
    override val participants: List<AbstractParty> = players

    fun getAllValidRoadsAttached(board: PlacedHexTiles, currLongestRoad: LinkedList<RoadState>, otherSettlements: Set<UniqueIdentifier>): Set<UniqueIdentifier> {
        // Create a reference list of road Ids
        val currLongestRoadIds = currLongestRoad.map { it.linearId }
        // Calculate all attached roads filtering out those that have already been included in the current longest road
        val allRoadsAttached = this.getAllRoadsAttached(board).filter { it !in currLongestRoadIds }.toSet()
        // Find the road previous to this one
        val previousRoad = currLongestRoad.filter { it.getAllRoadsAttached(board).contains(this.linearId) } - this
        // Remove any roads that wouldn't extend the currLongestRoad
        val roadsFilteredForDuplicatePaths = (allRoadsAttached - previousRoad.flatMap { it.getAllRoadsAttached(board) }).toSet()
        // Filter any roads that are blocked by settlements
        return roadsFilteredForDuplicatePaths.filter {
            // Get the first adjacent road to compare
            val compareRoad = board.getRoadById(it)
                    ?: throw IllegalArgumentException("The road does not exist on the given board.")
            // Determine the location of a potentially conflicting settlement between this road and the proposed addition
            val potentiallyConflictingLocation = compareRoad.getAdjacentCorners().intersect(this.absoluteSide.getAdjacentCorners().flatMap { corner -> board.getOverlappedCorners(corner) + corner }).single()
            // Retrieve the potentially conflicting settlement using the location
            val potentiallyConflictingSettlement = board.getSettlementOn(potentiallyConflictingLocation!!)
            // Return the set of roads filtered for roads blocked by the settlement of another player
            if (potentiallyConflictingSettlement == null) { true }
            else potentiallyConflictingSettlement !in otherSettlements
        }.toSet()
    }

    fun getAllRoadsAttached(board: PlacedHexTiles): Set<UniqueIdentifier> {
        val adjacentCorners = this.absoluteSide.getAdjacentCorners().map { (board.getOverlappedCorners(it) + it).filterNotNull() }.flatten()
        val allPossibleSides = adjacentCorners.flatMap { it.getAdjacentSides() } - this.absoluteSide
        return allPossibleSides.mapNotNull { board.getRoadOn(it) }.toSet()
    }

    /**
     * In Settlers of Catan, players earn additional victory points for maintaining the longest
     * road - so long as that continuous road is comprised of 5 or more adjacent roads. The methods
     * below are helpers to enable our keeping track of which roads might be the longest.
     */

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
 * of the board, roads, settlements, players and the current holder of the card.
 *
 * @param board The current state of the board game
 * @param roads All of the current road states
 * @param settlements All of the current settlement states
 * @param players All of the players involved in the board game
 */
@Suspendable
fun getLongestRoadHolder(board: PlacedHexTiles,
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
@Suspendable
fun assignLongestRoad(currentHolder: Party?, candidates: List<LongestRoadCandidate>): Party? {

    val orderedCandidates = candidates.sortedByDescending { it.longestRoadLength }

    if (orderedCandidates.isEmpty()) throw IllegalArgumentException("Candidates must be provided in order to assign the longest road.")

    // Longest candidate does not have at least 5 roads
    if (orderedCandidates[0].longestRoadLength < 5)
        return null

    return when {
        // Return the current holder if there is a tie
        orderedCandidates[0].longestRoadLength == orderedCandidates[1].longestRoadLength -> currentHolder
        // Check if only one player has the longest road
        orderedCandidates[0].longestRoadLength > orderedCandidates[1].longestRoadLength -> orderedCandidates[0].player
        // If more than one players have candidates of equal length, return null
        else -> null
    }
}

/**
 * Returns a list with the count of the longest road for each player.
 */
@Suspendable
fun longestRoadsForAllPlayers(board: PlacedHexTiles,
                              roads: List<RoadState>,
                              settlements: List<SettlementState>,
                              players: List<Party>): List<LongestRoadCandidate> {
    return players.map { LongestRoadCandidate(it, longestRoadForPlayer(board, roads, settlements, it).count()) }
}

/**
 * Returns a set of road ids with the longest road for a particular player.
 */
@Suspendable
fun longestRoadForPlayer(board: PlacedHexTiles,
                         roads: List<RoadState>,
                         settlements: List<SettlementState>,
                         player: Party): Set<UniqueIdentifier> {
    val playerRoadIds = roads.filter { it.owner == player }.toSet()
    val othersSettlements = settlements.filter { it.owner != player }.toSet()
    return calculateLongestRoad(board, playerRoadIds, othersSettlements)
}

/**
 * Calculates the longest road for a player.
 *
 * Roads must only contain roads for the player.
 * Settlements must only contain settlements of all the other players.
 *
 * @param board A built set of [HexTile]s representing the current state of the game
 * @param roadStates A set of [RoadState] belonging to a single player
 * @param settlements A set of settlements belonging to all players
 */
@Suspendable
private fun calculateLongestRoad(board: PlacedHexTiles,
                                 roadStates: Set<RoadState>,
                                 otherSettlements: Set<SettlementState>): Set<UniqueIdentifier> {

    val roads = roadStates.toMutableSet()
    val mapOfRoads = roads.map { it.linearId to it }.toMap()
    var longestRoad = setOf<UniqueIdentifier>()

    fun visitAndThenRecurse(roadId: UniqueIdentifier?, addAhead: Boolean, currLongestRoad: LinkedList<RoadState>, callBack: (LinkedList<RoadState>) -> Unit) {

        // Check if we should visit this road
        if (roadId == null) return

        // If the road doesn't exist and we are visiting in error, throw an exception
        val road = mapOfRoads[roadId] ?: throw IllegalArgumentException("Road does not exist.")

        // Visit the road
        roads.remove(road)

        // Recurse with the new visited road
        when(addAhead) {
            true -> { currLongestRoad.addLast(road) }
            false -> { currLongestRoad.addFirst(road) }
        }

        callBack(currLongestRoad)
        currLongestRoad.remove(road)
    }

    fun recurse(currLongestRoadParam: LinkedList<RoadState>? = null) {

        // Initialize the current longest road or use the passed in value
        val currLongestRoad = currLongestRoadParam ?: LinkedList(listOf(roads.unShift()))

        println(currLongestRoad.map { Pair(it.absoluteSide.tileIndex.value, it.absoluteSide.sideIndex.value) })
        // If the current road is longer than the longest road, set the longest road equal to the current longest road
        if (currLongestRoad.size > longestRoad.size){
            longestRoad = currLongestRoad.map { it.linearId }.toSet()
        }

        // If there are no more roads remaining, return.
        if (roads.isEmpty()) return

        // If the current longest road is empty, visit a new road
        val currLongestRoadIds = currLongestRoad.map { it.linearId }
        val currRoadStart = currLongestRoad.first()
        val currRoadEnd = currLongestRoad.last()

        currRoadEnd.getAllValidRoadsAttached(board, currLongestRoad, otherSettlements.map { it.linearId }.toSet()).filter { it !in currLongestRoadIds }. forEach {
            visitAndThenRecurse(it, true, currLongestRoad, ::recurse)
        }

        currRoadStart.getAllValidRoadsAttached(board, currLongestRoad, otherSettlements.map { it.linearId }.toSet()).filter { it !in currLongestRoadIds }.forEach {
            visitAndThenRecurse(it, false, currLongestRoad, ::recurse)
        }
    }

    while(roads.isNotEmpty()) {
        recurse()
    }

    return longestRoad
}

fun MutableSet<RoadState>.unShift(): RoadState {
    val roadRemoved = this.first()
    this.remove(roadRemoved)
    return roadRemoved
}

fun MutableSet<RoadState>.removeById(map: Map<UniqueIdentifier, RoadState>, roadId: UniqueIdentifier): RoadState {
    val road = map[roadId]
    this.remove(road)
    return road ?: throw IllegalArgumentException("Road does not exist.")
}

fun MutableSet<RoadState>.addNotNull(roadState: RoadState?): Boolean =
        if (roadState != null) {
            this.add(roadState)
            true
    } else { false }

fun MutableSet<RoadState>.removeNotNull(roadState: RoadState?): Boolean =
        if (roadState != null) {
            this.remove(roadState)
            true
        } else { false }



///**
// * Calculates the longest road for a player.
// *
// * Roads must only contain roads for the player.
// * Settlements must only contain settlements of all the other players.
// *
// * @param board A built set of [HexTile]s representing the current state of the game
// * @param roads A set of [RoadState] belonging to a single player
// * @param settlements A set of settlements belonging to all players
// */
//@Suspendable
//private fun calculateLongestRoad(board: PlacedHexTiles,
//                                 roads: Set<RoadState>,
//                                 settlements: Set<SettlementState>): Set<UniqueIdentifier> {
//    val roadIds = roads.map { it.linearId }.toSet()
//
//    if (roads.isEmpty()) throw IllegalArgumentException("A non-empty set of roads must be provided in order to calculate the longest road.")
//    if (roads.size > 1 && !roads.all { it.owner == roads.first().owner }) throw IllegalArgumentException("All of the provided roads must belong to a single player.")
//
//    val settlementsCorners = settlements.map { it.absoluteCorner }.toSet()
//
//    var longestRoad = setOf<UniqueIdentifier>()
//    val neverVisitedRoads = roads.map { it.linearId }.toMutableSet()
//
//    while (neverVisitedRoads.isNotEmpty()) {
//        val firstNeverVisitedRoad = roads.first { it.linearId == neverVisitedRoads.first() }
//        val candidate = calculateLongestRoad(
//                board, firstNeverVisitedRoad, roadIds, settlementsCorners)
//
//        if (candidate.count() > longestRoad.count())
//            longestRoad = candidate
//
//        neverVisitedRoads -= candidate
//    }
//
//    return longestRoad
//}
//
//@Suspendable
//private fun calculateLongestRoad(board: PlacedHexTiles,
//                                 roadState: RoadState,
//                                 playerRoads: Set<UniqueIdentifier>,
//                                 otherPlayersSettlements: Set<AbsoluteCorner>): Set<UniqueIdentifier> {
//    val startFromSide = roadState.absoluteSide
//    val startFromId = roadState.linearId
//    val absCorners = startFromSide.sideIndex
//            .getAdjacentCorners()
//            .map { AbsoluteCorner(startFromSide.tileIndex, it) }
//
//    val visited = calculateLongestRoad(board, startFromSide, absCorners.first(), setOf(), playerRoads, otherPlayersSettlements)
//
//    return calculateLongestRoad(board, startFromSide, absCorners.last(), visited - startFromId,
//            playerRoads - visited + startFromId, otherPlayersSettlements)
//}
//
//private fun calculateLongestRoad(board: PlacedHexTiles,
//                                 candidate: AbsoluteSide,
//                                 lastVisitedCorner: AbsoluteCorner,
//                                 visited: Set<UniqueIdentifier>,
//                                 available: Set<UniqueIdentifier>,
//                                 otherPlayersSettlements: Set<AbsoluteCorner>): Set<UniqueIdentifier> {
//
//    // Get the ID of the candidate board
//    val roadId = board.get(candidate.tileIndex).sides.getRoadOn(candidate.sideIndex)
//
//    // No road built
//    if (roadId == null) return visited
//
//    // Road from a different user
//    if (!available.contains(roadId)) return visited
//
//    // Road visited already
//    if (visited.contains(roadId)) return visited
//
//    // New visited list
//    val longestRoadVisited = visited + roadId
//
//    // Calculate next corner to expand
//    val nextCornerIndex = candidate.sideIndex.getAdjacentCorners() - lastVisitedCorner.cornerIndex
//    val nextCorner = AbsoluteCorner(candidate.tileIndex, nextCornerIndex.single())
//
//    // List with the corner and overlapped corners
//    val overlappedCorners = board.getOverlappedCorners(nextCorner).filterNotNull() + nextCorner
//
//    // If settlement from other player do not expand corner
//    if (otherPlayersSettlements.any { overlappedCorners.contains(it) }) {
//        return longestRoadVisited
//    }
//
//    val candidates = overlappedCorners.flatMap { absCorner ->
//        absCorner.cornerIndex.getAdjacentSides().map { side -> Pair(absCorner, AbsoluteSide(absCorner.tileIndex, side)) }
//    }
//
//    val visitedFromCandidates = candidates.map {
//        calculateLongestRoad(board, it.second, it.first, longestRoadVisited, available - longestRoadVisited, otherPlayersSettlements)
//    }
//
//    return visitedFromCandidates.maxBy { it.count() }!!.toSet()
//}
