package banking

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransferProcessorTest {
    @Test
    fun `successful transfer debits and credits accounts`() {
        val source = number("1111111111111111")
        val destination = number("2222222222222222")
        val processor = processor(source to "10.00", destination to "5.00")

        assertEquals(TransferResult.Applied, processor.process(transfer(source, destination, "3.25")))

        val snapshot = processor.snapshot()
        assertMoney("6.75", snapshot.getValue(source).balance)
        assertMoney("8.25", snapshot.getValue(destination).balance)
        assertEquals(listOf(source, destination), snapshot.keys.toList())
    }

    @Test
    fun `transfer may leave source at exactly zero`() {
        val source = number("1111111111111111")
        val destination = number("2222222222222222")
        val processor = processor(source to "3.25", destination to "5.00")

        assertEquals(TransferResult.Applied, processor.process(transfer(source, destination, "3.25")))
        assertMoney("0.00", processor.snapshot().getValue(source).balance)
    }

    @Test
    fun `insufficient funds is rejected atomically`() {
        val source = number("1111111111111111")
        val destination = number("2222222222222222")
        val processor = processor(source to "3.00", destination to "5.00")
        val before = processor.snapshot()

        assertRejected(
            TransferRejectionReason.INSUFFICIENT_FUNDS,
            processor.process(transfer(source, destination, "3.01"))
        )
        assertBalancesEqual(before, processor.snapshot())
    }

    @Test
    fun `self transfer on known account is rejected atomically`() {
        val known = number("1111111111111111")
        val processor = processor(known to "10.00")
        val before = processor.snapshot()

        assertRejected(TransferRejectionReason.SELF_TRANSFER, processor.process(transfer(known, known, "1.00")))
        assertBalancesEqual(before, processor.snapshot())
    }

    @Test
    fun `unknown source with known destination is rejected atomically`() {
        val destination = number("2222222222222222")
        val processor = TransferProcessor(mapOf(destination to Account(destination, money("5.00"))))
        val before = processor.snapshot()

        val result = processor.process(transfer(number("1111111111111111"), destination, "1.00"))

        assertRejected(TransferRejectionReason.UNKNOWN_SOURCE_ACCOUNT, result)
        assertBalancesEqual(before, processor.snapshot())
    }

    @Test
    fun `unknown destination is explicit and atomic`() {
        val source = number("1111111111111111")
        val processor = processor(source to "10.00")
        val before = processor.snapshot()

        val result = processor.process(transfer(source, number("2222222222222222"), "1.00"))

        assertRejected(TransferRejectionReason.UNKNOWN_DESTINATION_ACCOUNT, result)
        assertBalancesEqual(before, processor.snapshot())
    }

    @Test
    fun `rejection does not stop following valid transfer`() {
        val source = number("1111111111111111")
        val destination = number("2222222222222222")
        val processor = processor(source to "5.00", destination to "0.00")

        val results = processor.processAll(
            listOf(
                transfer(source, destination, "6.00"),
                transfer(source, destination, "2.00"),
            ),
        )

        assertRejected(TransferRejectionReason.INSUFFICIENT_FUNDS, results[0])
        assertEquals(TransferResult.Applied, results[1])
        assertMoney("3.00", processor.snapshot().getValue(source).balance)
        assertMoney("2.00", processor.snapshot().getValue(destination).balance)
    }

    @Test
    fun `processor preserves insertion order and owns defensive state`() {
        val first = number("1111111111111111")
        val second = number("2222222222222222")
        val initial = linkedMapOf(second to Account(second, money("2.00")), first to Account(first, money("1.00")))
        val processor = TransferProcessor(initial)
        initial.clear()

        val before: Map<AccountNumber, Account> = processor.snapshot()
        assertEquals(TransferResult.Applied, processor.process(transfer(first, second, "0.25")))
        val after: Map<AccountNumber, Account> = processor.snapshot()

        assertEquals(listOf(second, first), before.keys.toList())
        assertEquals(listOf(second, first), after.keys.toList())
        assertMoney("1.00", before.getValue(first).balance)
        assertMoney("2.00", before.getValue(second).balance)
        assertMoney("0.75", after.getValue(first).balance)
        assertMoney("2.25", after.getValue(second).balance)
    }

    @Test
    fun `processor rejects mismatched account map keys`() {
        val key = number("1111111111111111")
        val accountNumber = number("2222222222222222")
        assertFailsWith<IllegalArgumentException> {
            TransferProcessor(mapOf(key to Account(accountNumber, money("1.00"))))
        }
    }


    private fun processor(vararg balances: Pair<AccountNumber, String>): TransferProcessor = TransferProcessor(
        linkedMapOf(*balances.map { (number, balance) -> number to Account(number, money(balance)) }.toTypedArray()),
    )

    private fun transfer(from: AccountNumber, to: AccountNumber, amount: String) = Transfer(from, to, money(amount))

    private fun number(value: String) = AccountNumber(value)

    private fun money(value: String) = BigDecimal(value)

    private fun assertRejected(expected: TransferRejectionReason, actual: TransferResult) {
        assertEquals(expected, assertIs<TransferResult.Rejected>(actual).reason)
    }

    private fun assertBalancesEqual(expected: Map<AccountNumber, Account>, actual: Map<AccountNumber, Account>) {
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (number, account) ->
            assertMoney(
                account.balance.toPlainString(),
                actual.getValue(number).balance
            )
        }
    }

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertTrue(BigDecimal(expected).compareTo(actual) == 0, "Expected $expected but was $actual")
    }
}
