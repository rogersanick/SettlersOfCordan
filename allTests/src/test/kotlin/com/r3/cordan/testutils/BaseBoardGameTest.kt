package com.r3.cordan.testutils

import com.r3.cordan.primary.flows.board.SetupGameBoardFlow
import com.r3.cordan.primary.states.structure.GameBoardState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import java.lang.IllegalArgumentException
import java.sql.Savepoint

abstract class BaseBoardGameTest: BaseCordanTest() {

    // Get access to an array of playerNodes
    private val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)

    // Initialize snapshots of each node
    private val mutableMapOfSnapShots: MutableMap<Int, Savepoint?> = arrayOfAllPlayerNodes.map { it.id to null }.toMap().toMutableMap()

    val gameState = {
        val futureWithGameState = a.startFlow(SetupGameBoardFlow(p1, p2, p3, p4))
        network.runNetwork()
        val stxGameState = futureWithGameState.getOrThrow()
        stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()
    }()

    val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

    @BeforeAll
    fun setupAndTakeSnapshot() {
        setupGameBoardForTesting(gameState, network, arrayOfAllPlayerNodesInOrder)
        for (node in arrayOfAllPlayerNodes) {
            mutableMapOfSnapShots[node.id] = node.takeDBSnapShot()
        }
    }

    @BeforeEach
    fun prepare() {
        network.runNetwork()
    }

    @AfterEach
    override fun tearDown() {
        arrayOfAllPlayerNodesInOrder.forEach { it.rollBack(mutableMapOfSnapShots[it.id]) }
        network.stopNodes()
    }

    private fun StartedMockNode.takeDBSnapShot(): Savepoint {
        var savePoint: Savepoint? = null
        this.transaction {
            savePoint = this.services.jdbcSession().setSavepoint(this.id.toString())
        }
        return savePoint ?: throw Exception("A savepoint was not properly initialized.")
    }

    private fun StartedMockNode.rollBack(savepoint: Savepoint?) {
        if (savepoint == null) { throw IllegalArgumentException("You cannot restore to a Savepoint that doesn't exist.") }
        this.transaction {
            this.services.jdbcSession().rollback(savepoint)
        }
    }

}
