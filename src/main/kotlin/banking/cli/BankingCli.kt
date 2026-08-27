package banking.cli

import banking.TransferProcessor
import banking.csv.AccountBalancesCsvReader
import banking.csv.CsvInputException
import banking.csv.TransfersCsvReader
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.Path

object BankingCli {
    private const val SUCCESS = 0
    private const val FAILURE = 1
    private const val USAGE_ERROR = 2
    private const val USAGE = "Usage: banking-service <balances.csv> <transactions.csv>"

    /**
     * Runs the banking workflow for balances and transactions file paths.
     *
     * Returns 0 after processing, 1 for input failures,
     * and 2 for invalid command-line usage.
     */
    fun run(args: Array<String>, out: Appendable, err: Appendable): Int {
        if (args.size != 2) {
            return error(err, USAGE_ERROR, USAGE)
        }

        val balancesRaw = args[0]
        val balances = readInput(balancesRaw, "balances", err) { path ->
            AccountBalancesCsvReader().read(path)
        } ?: return FAILURE

        val transactionsRaw = args[1]
        val transfers = readInput(transactionsRaw, "transactions", err) { path ->
            TransfersCsvReader().read(path)
        } ?: return FAILURE

        val processor = TransferProcessor(balances)
        val results = processor.processAll(transfers)
        val report = ReportFormatter.format(transfers, results, processor.snapshot())

        out.append(report)
        return SUCCESS
    }

    private fun <T : Any> readInput(
        rawPath: String,
        inputKind: String,
        err: Appendable,
        read: (Path) -> T,
    ): T? = try {
        read(Path.of(rawPath))
    } catch (exception: CsvInputException) {
        error(err, FAILURE, "Invalid $inputKind CSV '$rawPath': ${description(exception)}")
        null
    } catch (exception: InvalidPathException) {
        readError(err, rawPath, exception)
        null
    } catch (exception: IOException) {
        readError(err, rawPath, exception)
        null
    } catch (exception: SecurityException) {
        readError(err, rawPath, exception)
        null
    }

    private fun readError(err: Appendable, rawPath: String, exception: Exception): Int {
        val reason = if (exception is NoSuchFileException) "file does not exist" else description(exception)
        return error(err, FAILURE, "Cannot read '$rawPath': $reason")
    }

    private fun error(err: Appendable, code: Int, message: String): Int {
        err.append(message).append('\n')
        return code
    }

    private fun description(exception: Exception): String =
        exception.message ?: exception.javaClass.simpleName
}
