package banking

import banking.cli.BankingCli
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val exitCode = BankingCli.run(args, System.out, System.err)
    System.out.flush()
    System.err.flush()
    exitProcess(exitCode)
}
