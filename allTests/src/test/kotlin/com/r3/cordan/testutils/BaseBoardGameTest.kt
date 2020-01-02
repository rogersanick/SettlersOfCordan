package com.r3.cordan.testutils

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import com.r3.cordan.primary.flows.board.SetupGameBoardFlow
import com.r3.cordan.primary.states.structure.GameBoardState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.config.NotaryConfig
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import org.junit.jupiter.api.*
import java.sql.Connection
import java.sql.ResultSet
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseBoardGameTest: BaseCordanTest() {

    // Get access to an array of playerNodes
    private val arrayOfAllInternalNodes = arrayListOf(internalA, internalB, internalC, internalD, internalOracle)
    private val setupTxs = arrayListOf<SecureHash>()

    val gameState = {
        val futureWithGameState = a.startFlow(SetupGameBoardFlow(p1, p2, p3, p4))
        network.runNetwork()
        val stxGameState = futureWithGameState.getOrThrow()
        setupTxs.add(stxGameState.id)
        stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()
    }()

    val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

    @BeforeAll
    fun setupGameBoard() {
        setupGameBoardForTestingAndReturnIds(
                gameState,
                network,
                arrayOfAllPlayerNodesInOrder
        ).forEach { setupTxs.add(it.id) }
    }

    @BeforeEach
    fun prepare() {
        network.runNetwork()
    }

    @AfterEach
    fun forgetEverything() {
        arrayOfAllInternalNodes.forEach {
            it.database.transaction {
                deleteAllTransactionsFromNodeTablesExcept(
                        it.services.jdbcSession(), setupTxs, customNodeTables)
            }
        }
        notary.database.transaction {
            deleteAllTransactionsFromNotaryExcept(notary.services.jdbcSession(), setupTxs)
        }
        network.restartNotary(notary)
    }

    @AfterAll
    override fun tearDown() {
        network.stopNodes()
    }

    private val defaultTransactionTables = listOf(
            "NODE_SCHEDULED_STATES",
            "VAULT_FUNGIBLE_STATES",
            "VAULT_FUNGIBLE_STATES_PARTS",
            "VAULT_LINEAR_STATES",
            "VAULT_LINEAR_STATES_PARTS",
            "VAULT_STATES",
            "VAULT_TRANSACTION_NOTES",
            "V_PKEY_HASH_EX_ID_MAP"
    )

    private val customNodeTables = listOf(
            "contract_turn_tracker_states"
    )

    /** Delete Transaction IDs [txIds] from all relevant Node tables.
     * Automatically deletes [txIds]from default tables and allows for additional [customTables] created by customers.
     * Note: [tx_id] in [NODE_TRANSACTIONS], but [transaction_id] everywhere else.
     */
    private fun deleteAllTransactionsFromNodeTablesExcept(
        jdbc: Connection,
        txIds: List<SecureHash>,
        customTables: List<String> = listOf()) {

        val stringifiedTxIds = "(" + txIds.fold("") { acc, sec -> if (acc.isEmpty()) "'$sec'" else "$acc, '$sec'" } + ")"

        // jdbc.prepareStatement("SELECT * FROM VAULT_STATES WHERE transaction_id = '${txIds.last()}'").execute()
        // Delete all previous test transactions from the NODE_TRANSACTIONS table
        jdbc.prepareStatement("DELETE FROM NODE_TRANSACTIONS WHERE tx_id NOT IN $stringifiedTxIds").execute()

        // Delete all previous test transactions from defaultTransactionTables
        for (defaultTransactionTable in defaultTransactionTables) {
            jdbc.prepareStatement("DELETE FROM $defaultTransactionTable WHERE transaction_id NOT IN $stringifiedTxIds").execute()
        }

        // Delete all previous test transactions from defaultTransactionTables
        for (customTable in customTables) {
            jdbc.prepareStatement("DELETE FROM $customTable WHERE transaction_id NOT IN $stringifiedTxIds").execute()
        }

        val gameBoardStateTransaction = setupTxs[setupTxs.size - 2]
        jdbc.prepareStatement("UPDATE VAULT_STATES SET STATE_STATUS=0 WHERE transaction_id='$gameBoardStateTransaction'").executeUpdate()
        val turnTrackerTransaction = setupTxs[setupTxs.size - 1]
        jdbc.prepareStatement("UPDATE VAULT_STATES SET STATE_STATUS=0 WHERE transaction_id='$turnTrackerTransaction'").executeUpdate()
    }

    fun StartedMockNode.restoreStatesInTx(txId: SecureHash, className: String? = null) {
        if (className != null) {
            this.services.jdbcSession().prepareStatement("UPDATE VAULT_STATES SET STATE_STATUS=0 WHERE transaction_id='$txId' and CONTRACT_STATE_CLASS_NAME='$className'").executeUpdate()
        } else {
            this.services.jdbcSession().prepareStatement("UPDATE VAULT_STATES SET STATE_STATUS=0 WHERE transaction_id='$txId'").executeUpdate()
        }
    }

    private fun deleteAllTransactionsFromNotaryExcept(
            jdbc: Connection,
            txIds: List<SecureHash>) {

        val stringifiedTxIds = "(" + txIds.fold("") { acc, sec -> if (acc.isEmpty()) "'$sec'" else "$acc, '$sec'" } + ")"

        val committedStatesTable = "NODE_NOTARY_COMMITTED_STATES"
        val committedTxsTable = "NODE_NOTARY_COMMITTED_TXS"
        val committedTxsRequestRecord = "NODE_NOTARY_REQUEST_LOG"

        // jdbc.prepareStatement("SELECT * FROM VAULT_STATES WHERE transaction_id = '${txIds.last()}'").execute()
        // Delete all previous test transactions from the NODE_TRANSACTIONS table
        jdbc.prepareStatement("DELETE FROM $committedStatesTable WHERE consuming_transaction_id NOT IN $stringifiedTxIds").execute()
        jdbc.prepareStatement("DELETE FROM $committedTxsTable WHERE transaction_id NOT IN $stringifiedTxIds").execute()
        jdbc.prepareStatement("DELETE FROM $committedTxsRequestRecord WHERE consuming_transaction_id NOT IN $stringifiedTxIds").execute()

    }

    fun getData(rs: ResultSet): List<String> {

        val rsmd = rs.metaData
        val columnsNumber = rsmd.columnCount
        val results  = arrayListOf<String>()

        // Iterate through the data in the result set and display it.
        while (rs.next()) { //Print one row
            for (i in 1..columnsNumber) {
                results.add (rs.metaData.getColumnName(i) + ": " + (rs.getString(i) ?: "NULL").toString() )
            }
        }

        val columnNames = getColumnNames(rs)
        return results
    }

    fun getColumnNames(rs: ResultSet): List<String> {
        val columnNames: MutableList<String> = ArrayList()
        for (i in 0 until rs.metaData.columnCount) {
            columnNames.add(rs.metaData.getColumnName(i + 1))
        }
        return columnNames
    }

    private fun InternalMockNetwork.restartNotary(node: TestStartedNode) {
        node.internals.disableDBCloseOnStop()
        node.dispose()
        network.createNode(InternalMockNodeParameters(
                legalName = node.internals.configuration.myLegalName,
                forcedID = node.internals.id,
                configOverrides = { doReturn(NotaryConfig(true)).whenever(it).notary }
        ))
    }

}