package com.testUtilities

import com.contractsAndStates.states.*
import com.flows.*
import com.oracleClientStatesAndContracts.states.DiceRollState
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.newSecureRandom
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.core.internal.signWithCert
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode

fun setupGameBoardForTesting(gameState: GameBoardState, network: MockNetwork, arrayOfAllPlayerNodesInOrder: List<StartedMockNode>, arrayOfAllTransactions: ArrayList<SignedTransaction>) {
    val nonconflictingHextileIndexAndCoordinatesRound1 = arrayListOf(Pair(0, 5), Pair(0, 3), Pair(2, 0), Pair(2, 2))
    val nonconflictingHextileIndexAndCoordinatesRound2 = arrayListOf(Pair(12, 5), Pair(12, 3), Pair(14, 0), Pair(14, 2))

    for (i in 0..3) {
        placeAPieceFromASpecificNodeAndEndTurn(i, nonconflictingHextileIndexAndCoordinatesRound1, gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions, false)
    }

    for (i in 3.downTo(0)) {
        placeAPieceFromASpecificNodeAndEndTurn(i, nonconflictingHextileIndexAndCoordinatesRound2, gameState, network, arrayOfAllPlayerNodesInOrder, arrayOfAllTransactions, false)
    }
}

fun getDiceRollWithSpecifiedRollValue(int1: Int, int2: Int, gameBoardState: GameBoardState, oracle: StartedMockNode): DiceRollState {
    val oracleParty = oracle.info.legalIdentities.first()
    val oraclePartyAndCert = oracle.info.legalIdentitiesAndCerts.first()
    val byteArrayOfDataToSign = byteArrayOf(int1.toByte(), int2.toByte(), gameBoardState.turnTrackerLinearId.hashCode().toByte(), gameBoardState.linearId.hashCode().toByte())
    val signatureOfOracleSigningOverData = oracleParty.signWithCert { DigitalSignatureWithCert(oraclePartyAndCert.certificate, byteArrayOfDataToSign) }
    return DiceRollState(
            int1,
            int2,
            gameBoardState.turnTrackerLinearId,
            gameBoardState.linearId,
            gameBoardState.players,
            signatureOfOracleSigningOverData
    )
}

fun getDiceRollWithRandomRollValue(gameBoardState: GameBoardState, oracle: StartedMockNode, cannotBe7: Boolean = true): DiceRollState {
    val oracleParty = oracle.info.legalIdentities.first()
    val oraclePartyAndCert = oracle.info.legalIdentitiesAndCerts.first()
    var int1 = newSecureRandom().nextInt(6) + 1
    val int2 = newSecureRandom().nextInt(6) + 1

    if (cannotBe7) {
        while (int1 + int2 == 7) {
            int1 = newSecureRandom().nextInt(6) + 1
        }
    }

    val byteArrayOfDataToSign = byteArrayOf(int1.toByte(), int2.toByte(), gameBoardState.turnTrackerLinearId.hashCode().toByte(), gameBoardState.linearId.hashCode().toByte())
    val signatureOfOracleSigningOverData = oracleParty.signWithCert { DigitalSignatureWithCert(oraclePartyAndCert.certificate, byteArrayOfDataToSign) }
    return DiceRollState (
            int1,
            int2,
            gameBoardState.turnTrackerLinearId,
            gameBoardState.linearId,
            gameBoardState.players,
            signatureOfOracleSigningOverData
    )
}