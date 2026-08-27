package banking.csv

import banking.Account
import banking.AccountNumber
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.LinkedHashMap

class AccountBalancesCsvReader {
    fun read(path: Path): Map<AccountNumber, Account> =
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use(::read)

    fun read(reader: Reader): Map<AccountNumber, Account> {
        val accounts = LinkedHashMap<AccountNumber, Account>()
        val lines = reader.buffered()
        var lineNumber = 0
        while (true) {
            val line = lines.readLine() ?: break
            lineNumber++
            val fields = CsvSupport.fields(line, 2, lineNumber)
            val number = CsvSupport.wrap(lineNumber, "Invalid account number '${fields[0]}'") {
                AccountNumber(fields[0])
            }
            if (accounts.containsKey(number)) {
                throw CsvInputException(lineNumber, "Duplicate account number '$number'")
            }
            val balance = CsvSupport.money(fields[1], lineNumber, "account balance")
            val account = CsvSupport.wrap(lineNumber, "Invalid account balance '${fields[1]}'") {
                Account(number, balance)
            }
            accounts[number] = account
        }
        return accounts
    }
}
