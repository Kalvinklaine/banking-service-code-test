package banking.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BankingCliTest {
    @Test
    fun `wrong argument counts print usage only to stderr and return exit code two`() {
        listOf(
            emptyArray<String>(),
            arrayOf("balances.csv"),
            arrayOf("balances.csv", "transfers.csv", "extra.csv"),
        ).forEach { args ->
            val out = StringBuilder()
            val err = StringBuilder()

            val exitCode = BankingCli.run(args, out, err)

            assertEquals(2, exitCode)
            assertEquals("", out.toString())
            assertEquals(
                "Usage: banking-service <balances.csv> <transactions.csv>\n",
                err.toString(),
            )
        }
    }

    @Test
    fun `successful run prints deterministic report only to stdout`() = withTempDirectory { directory ->
        val balances = directory.resolve("balances.csv")
        balances.writeText(
            "2000000000000000,20\n" +
                    "1000000000000000,10.5\n" +
                    "3000000000000000,0",
        )
        val transfers = directory.resolve("transfers.csv")
        transfers.writeText(
            "2000000000000000,3000000000000000,2.5\n" +
                    "1000000000000000,2000000000000000,1",
        )
        val out = StringBuilder()
        val err = StringBuilder()

        val exitCode = BankingCli.run(arrayOf(balances.toString(), transfers.toString()), out, err)

        assertEquals(0, exitCode)
        assertEquals("", err.toString())
        assertEquals(
            """Transfer results
1 2000000000000000 -> 3000000000000000 2.50 APPLIED
2 1000000000000000 -> 2000000000000000 1.00 APPLIED

Final balances
2000000000000000 18.50
1000000000000000 9.50
3000000000000000 2.50
""",
            out.toString(),
        )
    }

    @Test
    fun `supplied files produce the approved full report`() {
        // Gradle runs tests from the project root, deliberately exercising the supplied fixtures.
        val projectRoot = Path.of(System.getProperty("user.dir"))
        val balances = projectRoot.resolve("mable_account_balances.csv")
        val transfers = projectRoot.resolve("mable_transactions.csv")
        val out = StringBuilder()
        val err = StringBuilder()

        val exitCode = BankingCli.run(arrayOf(balances.toString(), transfers.toString()), out, err)

        assertEquals(0, exitCode)
        assertEquals("", err.toString())
        assertEquals(
            """Transfer results
1 1111234522226789 -> 1212343433335665 500.00 APPLIED
2 3212343433335755 -> 2222123433331212 1000.00 APPLIED
3 3212343433335755 -> 1111234522226789 320.50 APPLIED
4 1111234522221234 -> 1212343433335665 25.60 APPLIED

Final balances
1111234522226789 4820.50
1111234522221234 9974.40
2222123433331212 1550.00
1212343433335665 1725.60
3212343433335755 48679.50
""",
            out.toString(),
        )
    }

    @Test
    fun `malformed balances report their path only to stderr and return failure`() =
        withTempDirectory { directory ->
            val balances = directory.resolve("balances.csv")
            balances.writeText("bad,1")
            val transfers = directory.resolve("transfers.csv")
            transfers.writeText("")
            val out = StringBuilder()
            val err = StringBuilder()

            val exitCode = BankingCli.run(arrayOf(balances.toString(), transfers.toString()), out, err)

            assertEquals(1, exitCode)
            assertEquals("", out.toString())
            assertEquals(
                "Invalid balances CSV '$balances': " +
                        "Line 1: Invalid account number 'bad': Account number must contain exactly 16 ASCII digits\n",
                err.toString(),
            )
        }

    @Test
    fun `balances are validated before transactions`() = withTempDirectory { directory ->
        val balances = directory.resolve("balances.csv")
        balances.writeText("bad,1")
        val invalidTransfers = "transfers\u0000.csv"
        val out = StringBuilder()
        val err = StringBuilder()

        val exitCode = BankingCli.run(arrayOf(balances.toString(), invalidTransfers), out, err)

        assertEquals(1, exitCode)
        assertEquals("", out.toString())
        assertEquals(
            "Invalid balances CSV '$balances': " +
                    "Line 1: Invalid account number 'bad': Account number must contain exactly 16 ASCII digits\n",
            err.toString(),
        )
        assertFalse(err.toString().contains(invalidTransfers))
    }

    @Test
    fun `malformed transactions report their path only to stderr and return failure`() =
        withTempDirectory { directory ->
            val balances = directory.resolve("balances.csv")
            balances.writeText("1000000000000000,1")
            val transfers = directory.resolve("transfers.csv")
            transfers.writeText(
                "bad,1000000000000000,1",
            )
            val out = StringBuilder()
            val err = StringBuilder()

            val exitCode = BankingCli.run(arrayOf(balances.toString(), transfers.toString()), out, err)

            assertEquals(1, exitCode)
            assertEquals("", out.toString())
            assertEquals(
                "Invalid transactions CSV '$transfers': " +
                        "Line 1: Invalid source account number 'bad': " +
                        "Account number must contain exactly 16 ASCII digits\n",
                err.toString(),
            )
        }

    @Test
    fun `missing balances and transactions report concise actionable errors and return failure`() =
        withTempDirectory { directory ->
            val existingBalances = directory.resolve("balances.csv")
            existingBalances.writeText("1000000000000000,1")
            val existingTransfers = directory.resolve("transfers.csv")
            existingTransfers.writeText("")
            val missingBalances = directory.resolve("missing-balances.csv")
            val missingTransfers = directory.resolve("missing-transfers.csv")

            listOf(
                arrayOf(missingBalances.toString(), existingTransfers.toString()) to missingBalances,
                arrayOf(existingBalances.toString(), missingTransfers.toString()) to missingTransfers,
            ).forEach { (args, missingPath) ->
                val out = StringBuilder()
                val err = StringBuilder()

                val exitCode = BankingCli.run(args, out, err)

                assertEquals(1, exitCode)
                assertEquals("", out.toString())
                assertEquals(
                    "Cannot read '$missingPath': file does not exist\n",
                    err.toString(),
                )
            }
        }

    @Test
    fun `invalid balances path reports raw input and reason and returns failure`() {
        val invalidBalances = "balances\u0000.csv"
        val out = StringBuilder()
        val err = StringBuilder()

        val exitCode = BankingCli.run(arrayOf(invalidBalances, "transfers.csv"), out, err)

        assertEquals(1, exitCode)
        assertEquals("", out.toString())
        assertContains(err.toString(), "Cannot read '$invalidBalances': ")
        assertContains(err.toString(), "Nul character")
    }

    @Test
    fun `invalid transactions path after valid balances reports raw input and reason and returns failure`() =
        withTempDirectory { directory ->
            val balances = directory.resolve("balances.csv")
            balances.writeText("1000000000000000,1")
            val invalidTransfers = "transfers\u0000.csv"
            val out = StringBuilder()
            val err = StringBuilder()

            val exitCode = BankingCli.run(arrayOf(balances.toString(), invalidTransfers), out, err)

            assertEquals(1, exitCode)
            assertEquals("", out.toString())
            assertContains(err.toString(), "Cannot read '$invalidTransfers': ")
            assertContains(err.toString(), "Nul character")
        }

    @Test
    fun `business rejection remains successful and later valid transfer is applied`() =
        withTempDirectory { directory ->
            val balances = directory.resolve("balances.csv")
            balances.writeText(
                "1000000000000000,5\n" +
                        "2000000000000000,0\n" +
                        "3000000000000000,1",
            )
            val transfers = directory.resolve("transfers.csv")
            transfers.writeText(
                "1000000000000000,2000000000000000,10\n" +
                        "1000000000000000,3000000000000000,2",
            )
            val out = StringBuilder()
            val err = StringBuilder()

            val exitCode = BankingCli.run(arrayOf(balances.toString(), transfers.toString()), out, err)

            assertEquals(0, exitCode)
            assertEquals("", err.toString())
            assertEquals(
                """Transfer results
1 1000000000000000 -> 2000000000000000 10.00 REJECTED INSUFFICIENT_FUNDS
2 1000000000000000 -> 3000000000000000 2.00 APPLIED

Final balances
1000000000000000 3.00
2000000000000000 0.00
3000000000000000 3.00
""",
                out.toString(),
            )
        }

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("banking-cli-test")
        try {
            block(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
