package com.template.webserver

import com.contractsAndStates.states.GameBoardState
import com.flows.SetupGameBoardFlow
import jdk.nashorn.internal.ir.debug.JSONWriter
import jdk.nashorn.internal.runtime.JSONFunctions
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import nonapi.io.github.classgraph.json.JSONSerializer
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/") // The paths for HTTP requests are relative to this base path.
class Controller(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val proxy = rpc.proxy

    @PutMapping(value = "/gameboard", produces = arrayOf("text/plain"))
    private fun putGameBoard(@RequestBody listOfPlayers: ListOfPlayers): String {
        val player1 = proxy.wellKnownPartyFromX500Name(listOfPlayers.p1)!!
        val player2 = proxy.wellKnownPartyFromX500Name(listOfPlayers.p2)!!
        val player3 = proxy.wellKnownPartyFromX500Name(listOfPlayers.p3)!!
        val player4 = proxy.wellKnownPartyFromX500Name(listOfPlayers.p4)!!
        val txWithGameBoardStateProduced = proxy.startFlow(::SetupGameBoardFlow, player1, player2, player3, player4).returnValue.toCompletableFuture().getOrThrow()
        val producedGameBoardState = txWithGameBoardStateProduced.coreTransaction.outputsOfType<GameBoardState>().single()
        return JSONSerializer.serializeObject(producedGameBoardState)
    }

    @GetMapping(value = "/gameboard/{linearId:.+}", produces = arrayOf("text/plain"))
    private fun getGameBoard(@PathVariable linearId: String?): String {
        val gameBoardLinearId = UniqueIdentifier(linearId)
        val listOfReturnedGameBoardStates = proxy.vaultQueryByCriteria<GameBoardState>(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardLinearId)), GameBoardState::class.java)
        val gameBoardState = listOfReturnedGameBoardStates.states.single().state.data
        return JSONSerializer.serializeObject(gameBoardState)
    }
}

data class ListOfPlayers(
        val p1: CordaX500Name,
        val p2: CordaX500Name,
        val p3: CordaX500Name,
        val p4: CordaX500Name
)