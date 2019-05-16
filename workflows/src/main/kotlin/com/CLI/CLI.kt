package com.CLI

import com.flows.SetupGameBoardFlow

import net.corda.cliutils.ExitCodes
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.start
import net.corda.tools.shell.FlowShellCommand

class UsefulUtilityExitCodes: ExitCodes() {
    companion object {
        const val SETTLERS_OF_CORDAN_ERROR: Int = 666
        const val SUCCESS: Int = 205
    }
}

/**
 * TODO: I assumed this class would be auto-detected by the node and implemented at startup.
 * Instead, node-shell-extension must be run first and the node must be restarted
 * Need to adapt this function or the deployment process.
 */

class CordanCLI : CordaCliWrapper(
        "start-game", // the alias to be used for this utility in bash. When install-shell-extensions is run
        // you will be able to invoke this command by running <useful-utility --opts> from the command line
        "A utility CLI functions to start a game." // A description of this utility to be displayed when --help is run
) {
//    @CommandLine.Option(names = arrayOf("--extra-usefulness", "-e"),  // A list of the different ways this option can be referenced
//            description = arrayOf("Use this option to add extra usefulness") // Help description to be displayed for this option
//    )
//    private var extraUsefulness: Boolean = false // This default option will be shown in the help output

    override fun runProgram(): Int { // override this function to run the actual program
        try {
            val player1 = readLine()
            val player2 = readLine()
            val player3 = readLine()
            val player4 = readLine()
            FlowShellCommand().start(SetupGameBoardFlow::class.simpleName, mutableListOf(player1, player2, player3, player4))
        } catch (KnownException: Exception) {
            return UsefulUtilityExitCodes.SETTLERS_OF_CORDAN_ERROR // return a special exit code for known exceptions
        }

        return UsefulUtilityExitCodes.SUCCESS // this is the exit code to be returned to the system inherited from the ExitCodes base class
    }
}

fun main(args: Array<String>) {
    CordanCLI().start(args)
}