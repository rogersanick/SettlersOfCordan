package com.template.webserver.controllers

import com.r3.cordan.primary.flows.board.SetupGameBoardFlow
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.cordan.primary.states.board.GameBoardState
import com.template.webserver.NodeRPCConnection
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import nonapi.io.github.classgraph.json.JSONSerializer
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/gameboard") // The paths for HTTP requests are relative to this base path.
class GameBoardController(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val proxy = rpc.proxy

    @PutMapping(value = "/", produces = arrayOf("text/plain"))
    private fun putGameBoard(@RequestBody listOfPlayers: ListOfPlayers): String {
        val player1 = proxy.wellKnownPartyFromX500Name(listOfPlayers.p1)!!
        val player2 = proxy.wellKnownPartyFromX500Name(listOfPlayers.p2)!!
        val player3 = proxy.wellKnownPartyFromX500Name(listOfPlayers.p3)!!
        val player4 = proxy.wellKnownPartyFromX500Name(listOfPlayers.p4)!!
        val txWithGameBoardStateProduced = proxy.startFlow(::SetupGameBoardFlow, player1, player2, player3, player4).returnValue.toCompletableFuture().getOrThrow()
        val producedGameBoardState = txWithGameBoardStateProduced.coreTransaction.outputsOfType<GameBoardState>().single()
        return JSONSerializer.serializeObject(producedGameBoardState)
    }

    @GetMapping(value = "/{linearId:.+}", produces = arrayOf("text/plain"))
    private fun getGameBoard(@PathVariable linearId: String?): String {
        val gameBoardLinearId = UniqueIdentifier(linearId)
        val listOfReturnedGameBoardStates = proxy.vaultQueryByCriteria(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(gameBoardLinearId)), GameBoardState::class.java)
        val gameBoardState = listOfReturnedGameBoardStates.states.single().state.data
        return JSONSerializer.serializeObject(gameBoardState)
    }

    @GetMapping(value = "/", produces = arrayOf("text/plain"))
    private fun getGameBoards(): String {
        val listOfReturnedGameBoardStates = proxy.vaultQueryByCriteria(QueryCriteria.LinearStateQueryCriteria(), GameBoardState::class.java)
        val gameBoardStates = listOfReturnedGameBoardStates.states.map { it.state.data }
        return JSONSerializer.serializeObject(gameBoardStates)
    }

    @GetMapping(value = "/", produces = arrayOf("text/plain"))
    private fun getTokens(): String {
        val listOfTokens = proxy.vaultQueryByCriteria(QueryCriteria.LinearStateQueryCriteria(), FungibleToken::class.java)
        val fungibleTokens = listOfTokens.states.map { it.state.data }
        return JSONSerializer.serializeObject(fungibleTokens)
    }
}

data class ListOfPlayers(
        val p1: CordaX500Name,
        val p2: CordaX500Name,
        val p3: CordaX500Name,
        val p4: CordaX500Name
)