package banking.cli

import banking.Account
import banking.AccountNumber
import banking.Transfer
import banking.TransferRejectionReason
import banking.TransferResult
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReportFormatterTest {
    @Test
    fun `formats applied and rejected transfers and preserves final balance order`() {
        val first = AccountNumber("3000000000000000")
        val second = AccountNumber("1000000000000000")
        val third = AccountNumber("2000000000000000")
        val transfers = listOf(
            Transfer(first, second, BigDecimal("7")),
            Transfer(second, third, BigDecimal("12.3")),
        )
        val results = listOf(
            TransferResult.Applied,
            TransferResult.Rejected(TransferRejectionReason.INSUFFICIENT_FUNDS),
        )
        val finalBalances = linkedMapOf(
            third to Account(third, BigDecimal("4")),
            first to Account(first, BigDecimal("93.0")),
            second to Account(second, BigDecimal("17.00")),
        )

        val report = ReportFormatter.format(transfers, results, finalBalances)

        assertEquals(
            """Transfer results
1 3000000000000000 -> 1000000000000000 7.00 APPLIED
2 1000000000000000 -> 2000000000000000 12.30 REJECTED INSUFFICIENT_FUNDS

Final balances
2000000000000000 4.00
3000000000000000 93.00
1000000000000000 17.00
""",
            report,
        )
    }

    @Test
    fun `mismatched transfer and result counts are rejected with a useful message`() {
        val account = AccountNumber("1000000000000000")
        val exception = assertFailsWith<IllegalArgumentException> {
            ReportFormatter.format(
                listOf(Transfer(account, account, BigDecimal("1"))),
                emptyList(),
                emptyMap(),
            )
        }

        assertEquals("Transfer and result counts must match", exception.message)
    }

    @Test
    fun `empty inputs render both headings with one blank line and a final newline`() {
        assertEquals(
            "Transfer results\n\nFinal balances\n",
            ReportFormatter.format(emptyList(), emptyList(), emptyMap()),
        )
    }
}
