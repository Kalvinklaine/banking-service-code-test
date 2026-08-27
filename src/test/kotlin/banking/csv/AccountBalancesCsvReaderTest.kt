package banking.csv

import banking.AccountNumber
import java.io.StringReader
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AccountBalancesCsvReaderTest {
    private val reader = AccountBalancesCsvReader()

    @Test
    fun `valid records preserve order and normalize balances`() {
        val accounts = reader.read(
            StringReader(
                "0000000000000002,0\n" +
                        "0000000000000001,12.3\n" +
                        "0000000000000003,45.67",
            ),
        )

        assertEquals(
            listOf("0000000000000002", "0000000000000001", "0000000000000003"),
            accounts.keys.map(AccountNumber::value),
        )
        assertEquals(listOf("0.00", "12.30", "45.67"), accounts.values.map { it.balance.toPlainString() })
        assertTrue(accounts.values.all { it.balance.scale() == 2 })
    }

    @Test
    fun `header row is rejected`() {
        val exception = assertFailsWith<CsvInputException> {
            reader.read(StringReader("account,balance"))
        }

        assertEquals(1, exception.lineNumber)
    }

    @Test
    fun `empty file produces an empty ordered map`() {
        val accounts = reader.read(StringReader(""))

        assertTrue(accounts.isEmpty())
    }

    @Test
    fun `LF CRLF and final newline are valid line endings`() {
        listOf(
            "0000000000000001,1\n0000000000000002,2" to listOf(
                "0000000000000001" to "1.00",
                "0000000000000002" to "2.00",
            ),
            "0000000000000001,1\r\n0000000000000002,2\r\n" to listOf(
                "0000000000000001" to "1.00",
                "0000000000000002" to "2.00",
            ),
            "0000000000000001,1\n" to listOf("0000000000000001" to "1.00"),
        ).forEach { (input, expected) ->
            val accounts = reader.read(StringReader(input))

            assertEquals(
                expected,
                accounts.map { (accountNumber, account) ->
                    accountNumber.value to account.balance.toPlainString()
                },
            )
        }
    }

    @Test
    fun `trailing physical blank row is rejected on line two`() {
        listOf(
            "0000000000000001,1\n\n",
            "0000000000000001,1\r\n\r\n",
        ).forEach { input ->
            val exception = assertFailsWith<CsvInputException> { reader.read(StringReader(input)) }
            assertEquals(2, exception.lineNumber)
        }
    }

    @Test
    fun `duplicate account reports the duplicate line`() {
        val exception = assertFailsWith<CsvInputException> {
            reader.read(StringReader("0000000000000001,1\n0000000000000001,2"))
        }

        assertEquals(2, exception.lineNumber)
        assertTrue(exception.message.orEmpty().contains("Duplicate account number"))
    }

    @Test
    fun `wrong field counts and empty fields are rejected`() {
        listOf(
            "0000000000000001" to 1,
            "0000000000000001,1,extra" to 1,
            "0000000000000001,1," to 1,
            ",1" to 1,
            "0000000000000001," to 1,
        ).forEach { (input, expectedLine) ->
            val exception = assertFailsWith<CsvInputException> {
                reader.read(StringReader(input))
            }
            assertEquals(expectedLine, exception.lineNumber)
            assertTrue(exception.message.orEmpty().startsWith("Line $expectedLine:"))
        }
    }

    @Test
    fun `blank and whitespace-only physical lines are rejected`() {
        listOf("\n0000000000000001,1", "   \n0000000000000001,1", "0000000000000001,1\n\t\n0000000000000002,2")
            .forEachIndexed { index, input ->
                val exception = assertFailsWith<CsvInputException> { reader.read(StringReader(input)) }
                assertEquals(if (index == 2) 2 else 1, exception.lineNumber)
            }
    }

    @Test
    fun `invalid account representations are wrapped with line context`() {
        listOf(
            "123456789012345",
            "123456789012345a",
            "１２３４５６７８９０１２３４５６",
            " 123456789012345",
            "123456789012345 ",
            "12345678\t0123456",
        ).forEach { account ->
            val exception = assertFailsWith<CsvInputException> {
                reader.read(StringReader("0000000000000001,1\n$account,2"))
            }
            assertEquals(2, exception.lineNumber)
            assertTrue(exception.message.orEmpty().contains("Invalid account number"))
            assertIs<IllegalArgumentException>(exception.cause)
        }
    }

    @Test
    fun `invalid textual money forms are rejected strictly`() {
        listOf(
            "1e2",
            "+1",
            ".50",
            "10.",
            "1.234",
            " 1",
            "1 ",
            "\"1.00\"",
            "-1e2",
            "-.50",
            "-1.",
            "-1.234"
        ).forEach { balance ->
            val exception = assertFailsWith<CsvInputException> {
                reader.read(StringReader("0000000000000001,$balance"))
            }
            assertEquals(1, exception.lineNumber)
            assertTrue(exception.message.orEmpty().contains("Invalid account balance"))
        }
    }

    @Test
    fun `signed zero balances are accepted and normalized`() {
        listOf("-0", "-0.0", "-0.00").forEach { balance ->
            val account = reader.read(StringReader("0000000000000001,$balance")).values.single()

            assertEquals("0.00", account.balance.toPlainString())
            assertEquals(2, account.balance.scale())
        }
    }

    @Test
    fun `lexically valid negative balance preserves domain cause and line`() {
        val exception = assertFailsWith<CsvInputException> {
            reader.read(StringReader("0000000000000001,1\n0000000000000002,-0.01"))
        }

        assertEquals(2, exception.lineNumber)
        assertTrue(exception.message.orEmpty().contains("Invalid account balance"))
        assertTrue(exception.message.orEmpty().contains("Account balance cannot be negative"))
        assertIs<IllegalArgumentException>(exception.cause)
    }

    @Test
    fun `reader overload leaves caller-owned reader open`() {
        val input = TrackingReader("0000000000000001,1")

        reader.read(input)

        assertFalse(input.closed)
    }

    @Test
    fun `missing path propagates raw IO failure`() {
        val path = Files.createTempFile("missing-account-balances", ".csv")
        Files.delete(path)

        assertFailsWith<NoSuchFileException> { reader.read(path) }
    }

    @Test
    fun `path overload reads balances file`() {
        val path = Files.createTempFile("account-balances", ".csv")
        try {
            Files.writeString(path, "0000000000000001,12.34")

            val accounts = reader.read(path)

            assertEquals(BigDecimal("12.34"), accounts.getValue(AccountNumber("0000000000000001")).balance)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private class TrackingReader(content: String) : StringReader(content) {
        var closed = false

        override fun close() {
            closed = true
            super.close()
        }
    }
}
