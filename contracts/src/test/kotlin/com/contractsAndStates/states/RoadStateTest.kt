package com.contractsAndStates.states

import com.oracleClientStatesAndContracts.states.RollTrigger
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.core.TestIdentity
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals


class RoadStateTest {

    private lateinit var roads: MutableList<RoadState>
    private lateinit var builder: PlacedHexTiles.Builder
    private lateinit var board: PlacedHexTiles

    private var p1 = TestIdentity((CordaX500Name("player1", "New York", "GB")))
    private var p2 = TestIdentity((CordaX500Name("player2", "New York", "GB")))

    private fun buildRoads(owner: Party, pairs: List<Pair<Int, Int>>) = pairs.forEach {
        roads.add(RoadState(HexTileIndex(it.first), TileSideIndex(it.second), listOf(), owner, null, null))
        builder.buildRoad(roads.last().hexTileIndex, roads.last().hexTileSide, roads.last().linearId)
    }

    private fun buildBoard() {
        board = builder.build()
    }

    @Before
    fun init() {
        roads = mutableListOf()
        builder = PlacedHexTiles.Builder(getAllTileBuilders().toMutableList())
    }

    private fun getAllTileBuilders(): List<HexTile.Builder> {
        var tileIndex = 0
        return PlacedHexTiles.TILE_COUNT_PER_RESOURCE.flatMap { entry ->
            (0 until entry.value).map {
                HexTile.Builder(HexTileIndex(tileIndex).also { tileIndex++ })
                        .with(entry.key)
                        .with(RollTrigger(3))
                        .with(entry.key == HexTileType.Desert)
            }
        }
    }


    @Test
    fun `Longest road of 1`() {
        buildRoads(p1.party, listOf(0 to 1))
        buildBoard()
        assertEquals(1, longestRoad(board, roads))
    }

    @Test
    fun `longest road of 2 same hex`() {
        buildRoads(p1.party, listOf(0 to 1, 0 to 2))
        buildBoard()
        assertEquals(2, longestRoad(board, roads))
    }

    @Test
    fun `longest road of 3 same hex`() {
        buildRoads(p1.party, listOf(0 to 1, 0 to 2, 0 to 0))
        buildBoard()
        assertEquals(3, longestRoad(board, roads))
    }

    @Test
    fun `longest road of 6 same hex`() {
        buildRoads(p1.party, listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5))
        buildBoard()
        assertEquals(6, longestRoad(board, roads))
    }

    @Test
    fun `longest road of 2 different hex`() {
        buildRoads(p1.party, listOf(0 to 2, 1 to 3))
        buildBoard()
        assertEquals(2, longestRoad(board, roads))
    }

    @Test
    fun `longest road of 3 with split path`() {
        buildRoads(p1.party, listOf(0 to 2, 1 to 3, 1 to 4, 4 to 1))
        buildBoard()
        assertEquals(3, longestRoad(board, roads))
    }

    @Test
    fun `longest road of 8 with loop`() {
        buildRoads(p1.party, listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5, 1 to 3, 1 to 2))
        buildBoard()
        assertEquals(8, longestRoad(board, roads))
    }
}

fun longestRoad(board: PlacedHexTiles, roads: List<RoadState>): Int {
    var neverVisitedRoads = roads.map { it.linearId }.toSet()

    var longestRoad = listOf<UniqueIdentifier>()

    while (neverVisitedRoads.isNotEmpty()) {
        val candidate = longestRoadFromRoad(board, roads.first { it.linearId == neverVisitedRoads.first() }, roads)

        if (candidate.count() > longestRoad.count()) {
            longestRoad = candidate
            neverVisitedRoads = neverVisitedRoads - candidate
        }
    }

    return longestRoad.count()
}

fun longestRoadFromRoad(board: PlacedHexTiles, startFrom: RoadState, roads: List<RoadState>): List<UniqueIdentifier> {
    val absCorners = startFrom.hexTileSide.getAdjacentCorners().map { AbsoluteCorner(startFrom.hexTileIndex, it) }
    val absSide = AbsoluteSide(startFrom.hexTileIndex, startFrom.hexTileSide)

    val roadIdsA = roads.map { it.linearId }

    val visitedA = longestRoad(board, absSide, absCorners.first(), listOf(), roadIdsA)

    val visitedB = visitedA - startFrom.linearId
    val roadIdsB = roadIdsA - visitedA + startFrom.linearId

    return longestRoad(board, absSide, absCorners.last(), visitedB, roadIdsB)
}

fun longestRoad(board: PlacedHexTiles, candidate: AbsoluteSide, lastVisitedCorner: AbsoluteCorner, visited: List<UniqueIdentifier>, available: List<UniqueIdentifier>): List<UniqueIdentifier> {
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

    // TODO: If there is a settlement in overlappedCorners, return longestRoadVisited

    val candidates = overlappedCorners.flatMap { absCorner ->
        absCorner.cornerIndex.getAdjacentSides().map {
            side -> Pair(absCorner, AbsoluteSide(absCorner.tileIndex, side)) }
    }

    val visitedFromCandidates = candidates.map { longestRoad(board, it.second, it.first, longestRoadVisited, available - longestRoadVisited) }

    return visitedFromCandidates.maxBy { it.count() }!!
}
