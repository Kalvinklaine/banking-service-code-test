package banking.csv

import java.io.StringReader
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransfersCsvReaderTest {
    private val reader = TransfersCsvReader()

    @Test
    fun `valid records preserve order and normalize amounts`() {
        val transfers = reader.read(
            StringReader(
                "0000000000000002,0000000000000001,1\n" +
                        "0000000000000001,0000000000000003,12.3\n" +
                        "0000000000000003,0000000000000002,45.67",
            ),
        )

        assertEquals(
            listOf(
                Triple("0000000000000002", "0000000000000001", "1.00"),
                Triple("0000000000000001", "0000000000000003", "12.30"),
                Triple("0000000000000003", "0000000000000002", "45.67"),
            ),
            transfers.map { Triple(it.from.value, it.to.value, it.amount.toPlainString()) },
        )
        assertTrue(transfers.all { it.amount.scale() == 2 })
    }

    @Test
    fun `header row is rejected`() {
        val exception = assertFailsWith<CsvInputException> {
            reader.read(StringReader("from,to,amount"))
        }

        assertEquals(1, exception.lineNumber)
    }

    @Test
    fun `empty file produces an empty list`() {
        assertTrue(reader.read(StringReader("")).isEmpty())
    }

    @Test
    fun `LF CRLF and final newline are valid line endings`() {
        listOf(
            "0000000000000001,0000000000000002,1\n0000000000000002,0000000000000003,2" to listOf(
                Triple("0000000000000001", "0000000000000002", "1.00"),
                Triple("0000000000000002", "0000000000000003", "2.00"),
            ),
            "0000000000000001,0000000000000002,1\r\n0000000000000002,0000000000000003,2\r\n" to listOf(
                Triple("0000000000000001", "0000000000000002", "1.00"),
                Triple("0000000000000002", "0000000000000003", "2.00"),
            ),
            "0000000000000001,0000000000000002,1\n" to listOf(
                Triple("0000000000000001", "0000000000000002", "1.00"),
            ),
        ).forEach { (input, expected) ->
            val transfers = reader.read(StringReader(input))

            assertEquals(
                expected,
                transfers.map { Triple(it.from.value, it.to.value, it.amount.toPlainString()) },
            )
        }
    }

    @Test
    fun `trailing physical blank row is rejected on line two`() {
        listOf(
            "0000000000000001,0000000000000002,1\n\n",
            "0000000000000001,0000000000000002,1\r\n\r\n",
        ).forEach { input ->
            val exception = assertFailsWith<CsvInputException> { reader.read(StringReader(input)) }
            assertEquals(2, exception.lineNumber)
        }
    }

    @Test
    fun `wrong field counts and trailing empty fields are rejected`() {
        listOf(
            "0000000000000001,0000000000000002",
            "0000000000000001,0000000000000002,1,extra",
            "0000000000000001,0000000000000002,1,",
            ",0000000000000002,1",
            "0000000000000001,,1",
            "0000000000000001,0000000000000002,",
        ).forEach { input ->
            val exception = assertFailsWith<CsvInputException> { reader.read(StringReader(input)) }
            assertEquals(1, exception.lineNumber)
            assertTrue(exception.message.orEmpty().startsWith("Line 1:"))
        }
    }

    @Test
    fun `blank and whitespace-only physical lines are rejected`() {
        listOf(
            "\n0000000000000001,0000000000000002,1" to 1,
            "   \n0000000000000001,0000000000000002,1" to 1,
            "0000000000000001,0000000000000002,1\n\t\n0000000000000002,0000000000000003,2" to 2,
        ).forEach { (input, expectedLine) ->
            val exception = assertFailsWith<CsvInputException> { reader.read(StringReader(input)) }
            assertEquals(expectedLine, exception.lineNumber)
        }
    }

    @Test
    fun `malformed source account is wrapped with line context`() {
        val exception = assertFailsWith<CsvInputException> {
            reader.read(
                StringReader(
                    "0000000000000001,0000000000000002,1\n" +
                            "123456789012345a,0000000000000002,2",
                ),
            )
        }

        assertEquals(2, exception.lineNumber)
        assertTrue(exception.message.orEmpty().contains("Invalid source account number"))
        assertIs<IllegalArgumentException>(exception.cause)
    }

    @Test
    fun `malformed destination account is wrapped with line context`() {
        val exception = assertFailsWith<CsvInputException> {
            reader.read(StringReader("0000000000000001,１２３４５６７８９０１２３４５６,1"))
        }

        assertEquals(1, exception.lineNumber)
        assertTrue(exception.message.orEmpty().contains("Invalid destination account number"))
        assertIs<IllegalArgumentException>(exception.cause)
    }

    @Test
    fun `invalid textual amount forms are rejected strictly`() {
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
        ).forEach { amount ->
            val exception = assertFailsWith<CsvInputException> {
                reader.read(StringReader("0000000000000001,0000000000000002,$amount"))
            }
            assertEquals(1, exception.lineNumber)
            assertTrue(exception.message.orEmpty().contains("Invalid transfer amount"))
        }
    }

    @Test
    fun `zero and negative amounts are wrapped on their exact line`() {
        listOf("0", "0.00", "-0", "-0.00", "-1", "-0.01").forEach { amount ->
            val exception = assertFailsWith<CsvInputException> {
                reader.read(
                    StringReader(
                        "0000000000000001,0000000000000002,1\n" +
                                "0000000000000002,0000000000000003,$amount",
                    ),
                )
            }
            assertEquals(2, exception.lineNumber)
            assertTrue(exception.message.orEmpty().contains("Transfer amount must be positive"))
            assertIs<IllegalArgumentException>(exception.cause)
        }
    }

    @Test
    fun `structurally valid self-transfer row is parsed`() {
        val transfers = reader.read(StringReader("9999999999999999,9999999999999999,1"))

        assertEquals(1, transfers.size)
        assertEquals(transfers.single().from, transfers.single().to)
    }

    @Test
    fun `reader overload leaves caller-owned reader open`() {
        val input = TrackingReader("0000000000000001,0000000000000002,1")

        reader.read(input)

        assertFalse(input.closed)
    }

    @Test
    fun `missing path propagates raw IO failure`() {
        val path = Files.createTempFile("missing-transfers", ".csv")
        Files.delete(path)

        assertFailsWith<NoSuchFileException> { reader.read(path) }
    }

    @Test
    fun `path overload reads transfers file`() {
        val path = Files.createTempFile("transfers", ".csv")
        try {
            Files.writeString(path, "0000000000000001,0000000000000002,12.34")

            val transfers = reader.read(path)

            assertEquals("12.34", transfers.single().amount.toPlainString())
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
