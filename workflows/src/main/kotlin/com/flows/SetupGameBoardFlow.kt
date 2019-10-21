package com.flows

import co.paralleluniverse.fibers.Suspendable
import com.contractsAndStates.contracts.GameStateContract
import com.contractsAndStates.contracts.LongestRoadContract
import com.contractsAndStates.contracts.RobberContract
import com.contractsAndStates.contracts.TurnTrackerContract
import com.contractsAndStates.states.*
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker


// *******************
// * Game Start Flow *
// *******************

/**
 * This is the first flow run by a given node in order to propose the start of a new game to other
 * nodes on the network. In it's current implementation the flow requires the specification of which
 * parties will participate in the game. In the future there may be an opportunity to handle this auto-
 * matically through match making functionality.
 */

// TODO: Make this flow testable by separating GameBoard generation functionality from flow logic.

@InitiatingFlow(version = 1)
@StartableByRPC
class SetupGameBoardFlow(val p1: Party, val p2: Party, val p3: Party, val p4: Party) : FlowLogic<SignedTransaction>() {

    companion object {
        object GETTING_NOTARY : ProgressTracker.Step("Getting reference to the notary")
        object INITIALIZING_TRANSACTION : ProgressTracker.Step("Initializing the transaction and transaction builder")
        object ISSUING_COMMANDS : ProgressTracker.Step("Issuing the appropriate commands")
        object CREATING_A_TURN_TRACKER : ProgressTracker.Step("Creating a turn tracker for you buncha' cheaters")
        object SETTING_UP_YOUR_GAMEBOARD : ProgressTracker.Step("Setting up your personal GameBoard on Corda")
        object FINDING_A_VILLAIN_TO_PLAY_THE_ROBBER : ProgressTracker.Step("Finding a villain to play the robber")
        object FINALIZING_GAMEBOARD : ProgressTracker.Step("Finalizing your GameBoard on Corda")
        object ADDING_ALL_GAME_STATES_TO_THE_TRANSACTION : ProgressTracker.Step("Adding all states to the transaction")
        object VERIFYING : ProgressTracker.Step("Verifying the transaction")
        object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting signatures from your fellow citizens of Cordan")
        object FINALIZING_TRANSACTION : ProgressTracker.Step("Finalizing the transaction")
    }

    override val progressTracker = ProgressTracker(
            GETTING_NOTARY,
            INITIALIZING_TRANSACTION,
            ISSUING_COMMANDS,
            CREATING_A_TURN_TRACKER,
            SETTING_UP_YOUR_GAMEBOARD,
            FINDING_A_VILLAIN_TO_PLAY_THE_ROBBER,
            FINALIZING_GAMEBOARD,
            ADDING_ALL_GAME_STATES_TO_THE_TRANSACTION,
            VERIFYING,
            COLLECTING_SIGNATURES,
            FINALIZING_TRANSACTION
    )

    @Suspendable
    override fun call(): SignedTransaction {

        /**
         * The following objects define all of the steps required to execute the flow. These steps will
         * be executed in sequence to set up a game board and displayed to the user via a progress tracker.
         */
        // Step 1.  Get a reference to the notary service on the network
        progressTracker.currentStep = GETTING_NOTARY
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        // Step 2. Create a new transaction builder
        progressTracker.currentStep = INITIALIZING_TRANSACTION
        val tb = TransactionBuilder(notary)

        // Step 3 of  Create a new issue command and add it to the transaction.
        val playersList = listOf(p1, p2, p3, p4).shuffled()
        val playerKeys = playersList.map { it.owningKey }
        progressTracker.currentStep = ISSUING_COMMANDS
        val issueCommand = Command(
                GameStateContract.Commands.SetUpGameBoard(),
                playerKeys)
        val createTurnTracker = Command(
                TurnTrackerContract.Commands.CreateTurnTracker(),
                playerKeys)
        tb.addCommand(issueCommand)
        tb.addCommand(createTurnTracker)

        // Step 4. Generate data for new game state
        progressTracker.currentStep = SETTING_UP_YOUR_GAMEBOARD
        val boardBuilder = GameBoardState.Builder.createFull()

        // Step 5. Create a new turn tracker state
        progressTracker.currentStep = CREATING_A_TURN_TRACKER
        val turnTrackerState = TurnTrackerState(
                gameBoardLinearId = boardBuilder.linearId,
                participants = playersList)

        // Step 6. Create a robber state and issueRobber commands - add both to the transaction
        progressTracker.currentStep = FINDING_A_VILLAIN_TO_PLAY_THE_ROBBER
        val desertTile = boardBuilder.getTilesBy(HexTileType.Desert).single()
        val robberState = RobberState(boardBuilder.linearId, desertTile.hexTileIndex, playersList)
        val createRobberCommand = Command(
                RobberContract.Commands.CreateRobber(),
                playerKeys)
        tb.addOutputState(robberState, RobberContract.ID)
        tb.addCommand(createRobberCommand)

        progressTracker.currentStep = FINALIZING_GAMEBOARD
        val newGameState = boardBuilder
                .addPlayers(playersList)
                .withTurnTracker(turnTrackerState.linearId)
                .withRobber(robberState.linearId)
                .build()

        // Step 7. Initialise longest road state
        val longestRoadState = LongestRoadState(null, playersList)
        val createLongestRoadCommand = Command(LongestRoadContract.Commands.Init(), playerKeys)
        tb.addOutputState(longestRoadState, LongestRoadContract.ID)
        tb.addCommand(createLongestRoadCommand)

        // Step 8. Add the states to the transaction
        progressTracker.currentStep = ADDING_ALL_GAME_STATES_TO_THE_TRANSACTION
        tb.addOutputState(newGameState, GameStateContract.ID)
        tb.addOutputState(turnTrackerState, TurnTrackerContract.ID)

        // Step 9. Verify and sign the transaction
        progressTracker.currentStep = VERIFYING
        tb.verify(serviceHub)
        val ptx = serviceHub.signInitialTransaction(tb)

        // Step 10. Create a list of flows with the relevant participants
        progressTracker.currentStep = COLLECTING_SIGNATURES
        val sessions = (newGameState.participants - ourIdentity)
                .map { initiateFlow(it) }
                .toSet()

        // Step 11. Collect other signatures
        val stx = subFlow(CollectSignaturesFlow(ptx, sessions))

        // Step 12. Run the FinalityFlow
        progressTracker.currentStep = FINALIZING_TRANSACTION
        val playerNames = newGameState.players.map { it.name.toString() }
        val currPlayer = newGameState.players[0]

        // TODO: This messaging is not displaying
        println("\nYour unique game board identified is ${newGameState.linearId}")
        println("\nYou are playing with $playerNames")

        if (ourIdentity == currPlayer) {
            println("\nIt is your turn, you should use the BuildInitialSettlementAndRoadFlow to setup the board!")
        } else {
            println("\nIt is $currPlayer's turn")
        }

        return subFlow(FinalityFlow(stx, sessions))
    }
}

@InitiatedBy(SetupGameBoardFlow::class)
class SetupGameBoardFlowResponder(val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(counterpartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                stx.verify(serviceHub)
                println("\nSomeone has invited you to play Settlers of Cordan (Catan on Corda)\n")
                val gameBoardState = stx.coreTransaction.outputsOfType<GameBoardState>().single()
                val linearIDToBePrinted = gameBoardState.linearId
                val players = gameBoardState.players
                val playerNames = players.map { it.name.toString() }
                val currPlayer = players[0]
                println("\nYour unique game board identified is $linearIDToBePrinted")
                println("\nYou are playing with $playerNames")

                if (ourIdentity == currPlayer) {
                    println("\nIt is your turn, you should use the BuildInitialSettlementAndRoadFlow to setup the board!")
                } else {
                    println("\nIt is $currPlayer's turn")
                }
            }
        }

        val txWeJustSignedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = counterpartySession, expectedTxId = txWeJustSignedId.id))
    }
}