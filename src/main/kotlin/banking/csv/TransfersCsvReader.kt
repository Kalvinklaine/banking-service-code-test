package banking.csv

import banking.AccountNumber
import banking.Transfer
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class TransfersCsvReader {
    fun read(path: Path): List<Transfer> =
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use(::read)

    fun read(reader: Reader): List<Transfer> {
        val transfers = mutableListOf<Transfer>()
        val lines = reader.buffered()
        var lineNumber = 0
        while (true) {
            val line = lines.readLine() ?: break
            lineNumber++
            val fields = CsvSupport.fields(line, 3, lineNumber)
            val from = CsvSupport.wrap(lineNumber, "Invalid source account number '${fields[0]}'") {
                AccountNumber(fields[0])
            }
            val to = CsvSupport.wrap(lineNumber, "Invalid destination account number '${fields[1]}'") {
                AccountNumber(fields[1])
            }
            val amount = CsvSupport.money(fields[2], lineNumber, "transfer amount")
            val transfer = CsvSupport.wrap(lineNumber, "Invalid transfer amount '${fields[2]}'") {
                Transfer(from, to, amount)
            }
            transfers += transfer
        }
        return transfers
    }
}
