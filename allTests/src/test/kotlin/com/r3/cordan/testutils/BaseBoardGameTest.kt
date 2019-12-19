package com.r3.cordan.testutils

import com.r3.cordan.primary.flows.board.SetupGameBoardFlow
import com.r3.cordan.primary.states.structure.GameBoardState
import net.corda.core.crypto.SecureHash
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.startHikariPool
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.*
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.junit.jupiter.api.*
import java.lang.IllegalArgumentException
import java.sql.Connection
import java.sql.Savepoint

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseBoardGameTest: BaseCordanTest() {

    // Get access to an array of playerNodes
    private val arrayOfAllInternalNodes = arrayListOf(internalA, internalB, internalC, internalD)
    private val arrayOfAllPlayerNodes = arrayListOf(a, b, c, d)
    private lateinit var setupTxs: List<SecureHash>

    val gameState = {
        val futureWithGameState = a.startFlow(SetupGameBoardFlow(p1, p2, p3, p4))
        network.runNetwork()
        val stxGameState = futureWithGameState.getOrThrow()
        stxGameState.coreTransaction.outputsOfType<GameBoardState>().single()
    }()

    val arrayOfAllPlayerNodesInOrder = gameState.players.map { player -> arrayOfAllPlayerNodes.filter { it.info.chooseIdentity() == player }.first() }

    @BeforeAll
    fun setupGameBoard() {
        setupTxs = setupGameBoardForTestingAndReturnIds(
                gameState,
                network,
                arrayOfAllPlayerNodesInOrder
        ).map { it.id }
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
                        it.services.jdbcSession(), setupTxs)
            }
        }
    }

    @AfterAll
    override fun tearDown() {
        network.stopNodes()
    }

    val defaultTransactionTables = listOf(
            "NODE_SCHEDULED_STATES",
            "VAULT_FUNGIBLE_STATES",
            "VAULT_FUNGIBLE_STATES_PARTS",
            "VAULT_LINEAR_STATES",
            "VAULT_LINEAR_STATES_PARTS",
            "VAULT_STATES",
            "VAULT_TRANSACTION_NOTES",
            "V_PKEY_HASH_EX_ID_MAP"
    )

    /** Delete Transaction IDs [txIds] from all relevant Node tables.
     * Automatically deletes [txIds]from default tables and allows for additional [customTables] created by customers.
     * Note: [tx_id] in [NODE_TRANSACTIONS], but [transaction_id] everywhere else.
     */
    private fun deleteAllTransactionsFromNodeTablesExcept(
        jdbc: Connection,
        txIds: List<SecureHash>,
        customTables: List<String> = listOf()) {

        val stringifiedTxIds = "(" + txIds.fold("") { acc, sec -> if (acc.isEmpty()) "'$sec'" else "'$acc, $sec'" } + ")"

        jdbc.prepareStatement("DELETE FROM NODE_TRANSACTIONS WHERE tx_id NOT IN $stringifiedTxIds").execute()

        for (defaultTransactionTable in defaultTransactionTables) {
            jdbc.prepareStatement("DELETE FROM $defaultTransactionTable WHERE transaction_id NOT IN $stringifiedTxIds").execute()
        }

        // Delete from the custom tables
        for (customTable in customTables) {
            jdbc.prepareStatement("DELETE FROM $customTable WHERE transaction_id NOT IN $stringifiedTxIds").execute()
        }
    }

}


//        val database = configureDatabase(
//                hikariProperties = makeTestDataSourceProperties(),
//                databaseConfig = DatabaseConfig(),
//                wellKnownPartyFromAnonymous = internalA.services.identityService::wellKnownPartyFromAnonymous,
//                wellKnownPartyFromX500Name = internalA.services.identityService::wellKnownPartyFromX500Name,
//                schemaService = internalA.services.schemaService,
//                cacheFactory = internalA.services.cacheFactory,
//                ourName = internalA.info.legalIdentities.first().name
//        )
//        database.dataSource.connection