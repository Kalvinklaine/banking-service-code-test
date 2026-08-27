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

    fun run(args: Array<String>, out: Appendable, err: Appendable): Int {
        if (args.size != 2) {
            return error(err, USAGE_ERROR, USAGE)
        }

        val balancesRaw = args[0]
        val balancesPath = try {
            Path.of(balancesRaw)
        } catch (exception: InvalidPathException) {
            return readError(err, balancesRaw, exception)
        } catch (exception: SecurityException) {
            return readError(err, balancesRaw, exception)
        }
        val balances = try {
            AccountBalancesCsvReader().read(balancesPath)
        } catch (exception: CsvInputException) {
            return error(err, FAILURE, "Invalid balances CSV '$balancesRaw': ${description(exception)}")
        } catch (exception: IOException) {
            return readError(err, balancesRaw, exception)
        } catch (exception: SecurityException) {
            return readError(err, balancesRaw, exception)
        }

        val transactionsRaw = args[1]
        val transactionsPath = try {
            Path.of(transactionsRaw)
        } catch (exception: InvalidPathException) {
            return readError(err, transactionsRaw, exception)
        } catch (exception: SecurityException) {
            return readError(err, transactionsRaw, exception)
        }
        val transfers = try {
            TransfersCsvReader().read(transactionsPath)
        } catch (exception: CsvInputException) {
            return error(err, FAILURE, "Invalid transactions CSV '$transactionsRaw': ${description(exception)}")
        } catch (exception: IOException) {
            return readError(err, transactionsRaw, exception)
        } catch (exception: SecurityException) {
            return readError(err, transactionsRaw, exception)
        }

        val processor = TransferProcessor(balances)
        val results = processor.processAll(transfers)
        val report = ReportFormatter.format(transfers, results, processor.snapshot())

        out.append(report)
        return SUCCESS
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
