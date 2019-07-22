package com.contractsAndStates.states

import com.contractsAndStates.contracts.BuildPhaseContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

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
        val hexTileIndex: HexTileIndex,
        val hexTileSide: TileSideIndex,
        val players: List<Party>,
        val owner: Party,
        val roadAttachedA: UniqueIdentifier? = null,
        val roadAttachedB: UniqueIdentifier? = null,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {
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
}

fun longestRoad(board: PlacedHexTiles,
                roads: List<RoadState>,
                settlements: List<SettlementState>,
                players: List<Party>,
                currentHolder: Party?): Party? {
    val candidates = longestRoadsForAllPlayers(board, roads, settlements, players)
    return assignLongestRoad(currentHolder, candidates)
}

data class LongestRoadCandidate(val player: Party, val longestRoadLength: Int)

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


fun longestRoadsForAllPlayers(board: PlacedHexTiles,
                              roads: List<RoadState>,
                              settlements: List<SettlementState>,
                              players: List<Party>): List<LongestRoadCandidate> {
    return players.map { LongestRoadCandidate(it, longestRoadForPlayer(board, roads, settlements, it).count()) }
}

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
    val settlementsCorners = settlements.map { AbsoluteCorner(it.hexTileIndex, it.hexTileCoordinate) }.toSet()

    var longestRoad = setOf<UniqueIdentifier>()
    val neverVisitedRoads = roads.map { it.linearId }.toMutableSet()

    while (neverVisitedRoads.isNotEmpty()) {
        val firstNeverVisitedRoad = roads.first { it.linearId == neverVisitedRoads.first() }
        val candidate = calculateLongestRoad(
                board, AbsoluteSide(firstNeverVisitedRoad.hexTileIndex, firstNeverVisitedRoad.hexTileSide),
                firstNeverVisitedRoad.linearId, roadIds, settlementsCorners)

        if (candidate.count() > longestRoad.count())
            longestRoad = candidate

        neverVisitedRoads -= candidate
    }

    return longestRoad
}

private fun calculateLongestRoad(board: PlacedHexTiles,
                                 startFromSide: AbsoluteSide,
                                 startFromId: UniqueIdentifier,
                                 playerRoads: Set<UniqueIdentifier>,
                                 otherPlayersSettlements: Set<AbsoluteCorner>): Set<UniqueIdentifier> {

    val absCorners = startFromSide.sideIndex.getAdjacentCorners().map { AbsoluteCorner(startFromSide.tileIndex, it) }

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
    val roadId = board.get(candidate.tileIndex).sides.getRoadIdOn(candidate.sideIndex)

    // No road built
    if (roadId == null) return visited

    // Road from a different user
    if (!available.contains(roadId)) return visited

    // Road visited already
    if (visited.contains(roadId)) return visited

    // New visited list
    var longestRoadVisited = visited + roadId

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
