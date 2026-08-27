package banking

import banking.csv.AccountBalancesCsvReader
import banking.csv.TransfersCsvReader
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EndOfDayProcessingTest {
    @Test
    fun `supplied files produce expected end-of-day balances and conserve total`() {
        // Gradle runs tests from the project root, deliberately exercising the original supplied root fixtures without copies.
        // If runner setup changes, a dedicated Gradle projectDir system property can replace this resolution.
        val balancesPath = Path.of(System.getProperty("user.dir"), "mable_account_balances.csv")
        val transactionsPath = Path.of(System.getProperty("user.dir"), "mable_transactions.csv")
        assertTrue(Files.isReadable(balancesPath), "Supplied balances file is not readable: $balancesPath")
        assertTrue(Files.isReadable(transactionsPath), "Supplied transactions file is not readable: $transactionsPath")

        val balances = AccountBalancesCsvReader().read(balancesPath)
        val transfers = TransfersCsvReader().read(transactionsPath)
        assertEquals(
            listOf(
                Triple("1111234522226789", "1212343433335665", "500.00"),
                Triple("3212343433335755", "2222123433331212", "1000.00"),
                Triple("3212343433335755", "1111234522226789", "320.50"),
                Triple("1111234522221234", "1212343433335665", "25.60"),
            ),
            transfers.map { Triple(it.from.value, it.to.value, it.amount.toPlainString()) },
        )
        val expectedOrder = listOf(
            AccountNumber("1111234522226789"),
            AccountNumber("1111234522221234"),
            AccountNumber("2222123433331212"),
            AccountNumber("1212343433335665"),
            AccountNumber("3212343433335755"),
        )
        assertEquals(expectedOrder, balances.keys.toList())
        val beforeTotal = totalOf(balances.values)

        val processor = TransferProcessor(balances)
        val results = processor.processAll(transfers)

        assertEquals(4, results.size)
        assertTrue(results.all { it == TransferResult.Applied })
        val snapshot = processor.snapshot()
        assertEquals(expectedOrder, snapshot.keys.toList())
        val expectedBalances = listOf("4820.50", "9974.40", "1550.00", "1725.60", "48679.50")
        expectedOrder.zip(expectedBalances).forEach { (number, expectedBalance) ->
            assertMoney(expectedBalance, snapshot.getValue(number).balance)
        }
        assertMoney("66750.00", beforeTotal)
        assertMoney("66750.00", totalOf(snapshot.values))
    }

    private fun totalOf(accounts: Collection<Account>): BigDecimal =
        accounts.fold(BigDecimal.ZERO) { total, account -> total + account.balance }

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertTrue(BigDecimal(expected).compareTo(actual) == 0, "Expected $expected but was $actual")
    }
}
