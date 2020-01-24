package com.r3.cordan.primary.flows.development

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.cordan.primary.contracts.development.DevelopmentCardContract
import com.r3.cordan.primary.contracts.robber.PlayBlockerContract
import com.r3.cordan.primary.contracts.robber.RobberContract
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.service.GenerateSpendService
import com.r3.cordan.primary.states.board.GameBoardState
import com.r3.cordan.primary.states.board.HexTileIndex
import com.r3.cordan.primary.states.development.FaceDownDevelopmentCardState
import com.r3.cordan.primary.states.development.RevealedDevelopmentCardState
import com.r3.cordan.primary.states.resources.GameCurrencyState
import com.r3.cordan.primary.states.resources.Resource
import com.r3.cordan.primary.states.robber.BlockedStatus
import com.r3.cordan.primary.states.robber.PlayBlockerState
import com.r3.cordan.primary.states.robber.RobberState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder


private fun getFaceDownDevelopmentCard(revealedDevelopmentCardStateAndRef: StateAndRef<RevealedDevelopmentCardState>, serviceHub: ServiceHub)
        = serviceHub
            .vaultService
            .querySingleState<FaceDownDevelopmentCardState>(revealedDevelopmentCardStateAndRef.state.data.faceDownDevelopmentCardId)

private fun getGameBoard(gameBoardLinearId: UniqueIdentifier, serviceHub: ServiceHub)
        = serviceHub
        .vaultService
        .querySingleState<GameBoardState>(gameBoardLinearId).state.data

fun TransactionBuilder.playYearOfPlenty(
        serviceHub: ServiceHub,
        revealedDevelopmentCardStateAndRef: StateAndRef<RevealedDevelopmentCardState>,
        resourcesToIssue: Map<TokenType, Long>
) {
    val ourIdentity = serviceHub.myInfo.legalIdentities.first()
    val faceDownDevelopmentCardStateAndRef = getFaceDownDevelopmentCard(revealedDevelopmentCardStateAndRef, serviceHub)
    val gameBoardState = getGameBoard(faceDownDevelopmentCardStateAndRef.state.data.gameBoardId, serviceHub)
    this.addInputState(revealedDevelopmentCardStateAndRef)
    this.addInputState(faceDownDevelopmentCardStateAndRef)
    for (resource in resourcesToIssue) {
        serviceHub.cordaService(GenerateSpendService::class.java)
                .generateInGameSpend(gameBoardState.linearId, this, resourcesToIssue, ourIdentity, ourIdentity)
    }
    this.addCommand(Command(DevelopmentCardContract.Commands.PlayYearOfPlenty(), gameBoardState.playerKeys()))
}

private fun TransactionBuilder.playKnight(
        serviceHub: ServiceHub,
        revealedDevelopmentCardStateAndRef: StateAndRef<RevealedDevelopmentCardState>,
        hexTileIndex: Int,
        targetPlayer: Party
) {
    val faceDownDevelopmentCardStateAndRef = getFaceDownDevelopmentCard(revealedDevelopmentCardStateAndRef, serviceHub)
    val gameBoardState = getGameBoard(faceDownDevelopmentCardStateAndRef.state.data.gameBoardId, serviceHub)
    val targetHexTileIndex = HexTileIndex(hexTileIndex)
    val robberStateAndRef = serviceHub.vaultService
            .querySingleState<RobberState>(gameBoardState.robberLinearId)
    val newRobberState = robberStateAndRef.state.data.moveAndActivate(targetHexTileIndex, targetPlayer)
    this.addInputState(revealedDevelopmentCardStateAndRef)
    this.addInputState(faceDownDevelopmentCardStateAndRef)
    this.addInputState(robberStateAndRef)
    this.addOutputState(newRobberState)
    this.addCommand(Command(DevelopmentCardContract.Commands.PlayYearOfPlenty(), gameBoardState.playerKeys()))
    this.addCommand(Command(RobberContract.Commands.MoveRobberWithKnight(), gameBoardState.playerKeys()))
}

private fun TransactionBuilder.playRoadBuilding(
        serviceHub: ServiceHub,
        revealedDevelopmentCardStateAndRef: StateAndRef<RevealedDevelopmentCardState>,
        faceDownDevelopmentCardStateAndRef: StateAndRef<FaceDownDevelopmentCardState>
) {

}

private fun TransactionBuilder.playVictoryPoint(
        serviceHub: ServiceHub,
        revealedDevelopmentCardStateAndRef: StateAndRef<RevealedDevelopmentCardState>,
        faceDownDevelopmentCardStateAndRef: StateAndRef<FaceDownDevelopmentCardState>
) {
}

private fun TransactionBuilder.playMonopoly(
        serviceHub: ServiceHub,
        revealedDevelopmentCardStateAndRef: StateAndRef<RevealedDevelopmentCardState>,
        faceDownDevelopmentCardStateAndRef: StateAndRef<FaceDownDevelopmentCardState>
) {

    val faceDownDevelopmentCardState = faceDownDevelopmentCardStateAndRef.state.data
    val currentResources = serviceHub.vaultService.queryBy<GameCurrencyState>().states
            .map { it.state.data }
            .groupBy { it.holder }

    currentResources.forEach {
        val totalToBePaid = it.value.sumBy { token -> token.fungibleToken.amount.quantity.toInt() }
        this.addInputState(faceDownDevelopmentCardStateAndRef)
        this.addInputState(revealedDevelopmentCardStateAndRef)
        this.addOutputState(PlayBlockerState(
                it.key as Party,
                faceDownDevelopmentCardState.players,
                totalToBePaid,
                BlockedStatus.MUST_PAY_RESOURCES_TO_PLAYER,
                it.value.first().gameBoardId,
                mapOf(it.value.first().fungibleToken.tokenType as Resource to totalToBePaid)
        ))
        this.addCommand(Command(
                PlayBlockerContract.Commands.IssuePlayBlockers(),
                faceDownDevelopmentCardState.players.map { player -> player.owningKey }))
        this.addCommand(Command(
                DevelopmentCardContract.Commands.PlayMonopoly(),
                faceDownDevelopmentCardState.players.map { player -> player.owningKey }))
    }
}