package com.r3.cordan.primary.flows.development

import com.r3.cordan.primary.contracts.development.DevelopmentCardContract
import com.r3.cordan.primary.contracts.robber.PlayBlockerContract
import com.r3.cordan.primary.flows.querySingleState
import com.r3.cordan.primary.states.board.AbsoluteSide
import com.r3.cordan.primary.states.development.FaceDownDevelopmentCardState
import com.r3.cordan.primary.states.development.RevealedDevelopmentCardState
import com.r3.cordan.primary.states.resources.GameCurrencyState
import com.r3.cordan.primary.states.resources.Resource
import com.r3.cordan.primary.states.robber.BlockedStatus
import com.r3.cordan.primary.states.robber.PlayBlockerState
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder

fun TransactionBuilder.playMonopoly(
        serviceHub: ServiceHub,
        revealedDevelopmentCardStateAndRef: StateAndRef<RevealedDevelopmentCardState>
) {
    val revealedDevelopmentCardState = revealedDevelopmentCardStateAndRef.state.data
    val faceDownDevelopmentCardStateAndRef = serviceHub.vaultService
            .querySingleState<FaceDownDevelopmentCardState>(revealedDevelopmentCardState.faceDownDevelopmentCardId)
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