package com.r3.cordan.primary.states

import com.r3.cordan.oracle.client.states.RollTrigger
import com.r3.cordan.primary.states.board.*
import com.r3.cordan.primary.states.resources.HexTileType
import com.r3.cordan.primary.states.structure.*
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.core.TestIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class RoadStateTest {
    
    private lateinit var settlements: MutableSet<SettlementState>
    private lateinit var roads: MutableSet<RoadState>
    private lateinit var builder: PlacedHexTiles.Builder
    private lateinit var board: PlacedHexTiles

    private var p1 = TestIdentity((CordaX500Name("player1", "New York", "GB")))
    private var p2 = TestIdentity((CordaX500Name("player2", "New York", "GB")))
    private var p3 = TestIdentity((CordaX500Name("player3", "New York", "GB")))
    private var p4 = TestIdentity((CordaX500Name("player4", "New York", "GB")))

    private fun buildRoads(owner: Party, pairs: List<Pair<Int, Int>>) = pairs.forEach {
        // Build a version of the board based on the latest hexTiles
        buildBoard()

        // Get all adjacent absolute corners corresponding to the absolute side
        val absoluteSide = AbsoluteSide(HexTileIndex(it.first), TileSideIndex(it.second))
        val oppositeAbsoluteSide = board.getOpposite(absoluteSide)

        roads.add(RoadState(
                gameBoardLinearId = UniqueIdentifier(),
                absoluteSide = AbsoluteSide(HexTileIndex(it.first), TileSideIndex(it.second)),
                players = listOf(),
                owner = owner
        ))

        builder.setRoadOn(roads.last().absoluteSide, roads.last().linearId)
    }

    private fun buildSettlements(owner: Party, pairs: List<Pair<Int, Int>>) = pairs.forEach {
        val settlementState = SettlementState(
                UniqueIdentifier(),
                AbsoluteCorner(HexTileIndex(it.first), TileCornerIndex(it.second)), listOf(), owner
        )
        settlements.add(settlementState)
        builder.setSettlementOn(settlementState.absoluteCorner, settlementState.linearId)
    }

    private fun buildBoard() {
        board = builder.build()
    }

    @BeforeEach
    fun init() {
        settlements = mutableSetOf()
        roads = mutableSetOf()
        builder = PlacedHexTiles.Builder(getAllTileBuilders().toMutableList())
    }

    private fun getAllTileBuilders(): List<HexTile.Builder> {
        var tileIndex = 0
        return PlacedHexTiles.tileCountPerType.flatMap { entry ->
            (0 until entry.value).map {
                HexTile.Builder(HexTileIndex(tileIndex).also { tileIndex++ })
                        .with(entry.key)
                        .with(if (entry.key == HexTileType.Desert) null else RollTrigger(3))
                        .with(entry.key == HexTileType.Desert)
            }
        }
    }


    @Test
    fun `Longest road of 1`() {
        buildRoads(p1.party, listOf(0 to 1))
        buildBoard()
        assertEquals(1, longestRoadForPlayer(board, roads.toList(), listOf(), p1.party).count())
    }

    @Test
    fun `longest road of 2 same hex`() {
        buildRoads(p1.party, listOf(0 to 1, 0 to 2))
        buildBoard()
        assertEquals(2, longestRoadForPlayer(board, roads.toList(), listOf(), p1.party).count())
    }

    @Test
    fun `longest road of 3 same hex`() {
        buildRoads(p1.party, listOf(0 to 1, 0 to 2, 0 to 0))
        buildBoard()
        assertEquals(3, longestRoadForPlayer(board, roads.toList(), listOf(), p1.party).count())
    }

    @Test
    fun `longest road of 6 same hex`() {
        buildRoads(p1.party, listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5))
        buildBoard()
        assertEquals(6, longestRoadForPlayer(board, roads.toList(), listOf(), p1.party).count())
    }

    @Test
    fun `longest road of 2 different hex`() {
        buildRoads(p1.party, listOf(0 to 2, 1 to 3))
        buildBoard()
        assertEquals(2, longestRoadForPlayer(board, roads.toList(), listOf(), p1.party).count())
    }

    @Test
    fun `longest road of 3 with split path`() {
        buildRoads(p1.party, listOf(0 to 2, 1 to 3, 1 to 4, 4 to 1))
        buildBoard()
        assertEquals(3, longestRoadForPlayer(board, roads.toList(), listOf(), p1.party).count())
    }

    @Test
    fun `longest road of 8 with loop`() {
        buildRoads(p1.party, listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5, 1 to 3, 1 to 2))
        buildBoard()
        assertEquals(8, longestRoadForPlayer(board, roads.toList(), listOf(), p1.party).count())
    }

    @Test
    fun `longest road of 13 with 2 loops`() {
        buildRoads(p1.party, listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5, 1 to 3, 1 to 2,
                5 to 0, 5 to 1, 5 to 2, 5 to 3, 5 to 4))
        buildBoard()
        assertEquals(13, longestRoadForPlayer(board, roads.toList(), listOf(), p1.party).count())
    }

    @Test
    fun `road of 2 with 1 settlement in the middle in the same hex`() {
        buildRoads(p1.party, listOf(5 to 0, 5 to 1))
        buildSettlements(p2.party, listOf(5 to 1))
        buildBoard()
        assertEquals(1, longestRoadForPlayer(board, roads.toList(), settlements.toList(), p1.party).count())
    }

    @Test
    fun `road of 2 with 1 own settlement in the middle`() {
        buildRoads(p1.party, listOf(5 to 0, 5 to 1))
        buildSettlements(p1.party, listOf(6 to 5))
        buildBoard()
        assertEquals(2, longestRoadForPlayer(board, roads.toList(), settlements.toList(), p1.party).count())
    }

    @Test
    fun `longest road of 1 board setup state`() {
        buildRoads(p1.party, listOf(0 to 5, 12 to 5))
        buildRoads(p2.party, listOf(0 to 3, 12 to 3))
        buildRoads(p3.party, listOf(2 to 0, 14 to 0))
        buildRoads(p4.party, listOf(2 to 2, 14 to 2))
        buildSettlements(p1.party, listOf(0 to 5, 12 to 5))
        buildSettlements(p2.party, listOf(0 to 3, 12 to 3))
        buildSettlements(p3.party, listOf(2 to 0, 14 to 0))
        buildSettlements(p4.party, listOf(2 to 2, 14 to 2))

        buildSettlements(p1.party, listOf(15 to 2))
        buildRoads(p1.party, listOf(15 to 1))
        buildBoard()
        assertEquals(1, longestRoadForPlayer(board, roads.toList(), settlements.toList(), p1.party).count())
    }

    @Test
    fun `longest road of 5 with split path`() {
        buildRoads(p1.party, listOf(0 to 2, 1 to 3, 1 to 2, 1 to 1, 2 to 3))
        buildBoard()
        assertEquals(4, longestRoadForPlayer(board, roads.toList(), listOf(), p1.party).count())
    }

    @Test
    fun `longest road of 12 with two complete hexes`() {
        buildRoads(p1.party, listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4, 0 to 5, 1 to 3,
                5 to 0, 5 to 1, 5 to 2, 5 to 3, 5 to 4, 5 to 5))
        buildBoard()
        assertEquals(4, longestRoadForPlayer(board, roads.toList(), listOf(), p1.party).count())
    }
}

class AssignLongestRoadTests {

    private var p1 = TestIdentity((CordaX500Name("player1", "New York", "GB")))
    private var p2 = TestIdentity((CordaX500Name("player2", "New York", "GB")))
    private var p3 = TestIdentity((CordaX500Name("player3", "New York", "GB")))
    private var p4 = TestIdentity((CordaX500Name("player4", "New York", "GB")))

    private fun createCandidates(vararg lengths: Int) = listOf(p1, p2, p3, p4)
            .mapIndexed { index, testIdentity ->
                LongestRoadCandidate(testIdentity.party, lengths[index])
            }

    @Test
    fun `No candidate has at least 5 roads and no previous holder`() {
        val candidates = createCandidates(1, 2, 3, 4)
        assertEquals(null, assignLongestRoad(null, candidates))
    }

    @Test
    fun `Previous holder nor candidate have at least 5 roads anymore`() {
        val candidates = createCandidates(1, 2, 3, 4)
        assertEquals(null, assignLongestRoad(p1.party, candidates))
    }

    @Test
    fun `Only one player has the longest road and no previous holder`() {
        val candidates = createCandidates(1, 5, 3, 4)
        assertEquals(p2.party, assignLongestRoad(null, candidates))
    }

    @Test
    fun `New player with longest road`() {
        val candidates = createCandidates(5, 6, 3, 4)
        assertEquals(p2.party, assignLongestRoad(p1.party, candidates))
    }

    @Test
    fun `Another player has same length as current holder`() {
        val candidates = createCandidates(5, 5, 3, 4)
        assertEquals(null, assignLongestRoad(p2.party, candidates))
    }

    @Test
    fun `More than one player same length and longer than current holder`() {
        val candidates = createCandidates(5, 7, 7, 5)
        assertEquals(null, assignLongestRoad(p4.party, candidates))
    }
}
