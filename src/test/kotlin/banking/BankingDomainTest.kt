package banking

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BankingDomainTest {
    @Test
    fun `account number accepts exactly sixteen ASCII digits with value semantics`() {
        val accountNumber = AccountNumber("1234567890123456")
        val sameAccountNumber = AccountNumber("1234567890123456")

        assertEquals("1234567890123456", accountNumber.value)
        assertEquals(accountNumber, sameAccountNumber)
        assertEquals(accountNumber.hashCode(), sameAccountNumber.hashCode())
        assertEquals("1234567890123456", accountNumber.toString())
    }

    @Test
    fun `account number rejects invalid representations`() {
        listOf(
            "123456789012345",
            "12345678901234567",
            "123456789012345a",
            "１２３４５６７８９０１２３４５６",
            " 123456789012345",
            "123456789012345 ",
        ).forEach { value -> assertFailsWith<IllegalArgumentException> { AccountNumber(value) } }
    }

    @Test
    fun `account validates and normalizes balance`() {
        val number = number("1111111111111111")
        assertMoney("0.00", Account(number, BigDecimal.ZERO).balance)
        val account = Account(number, BigDecimal("12.3"))
        assertMoney("12.30", account.balance)
        assertEquals(2, account.balance.scale())
        assertEquals(account, Account(number, BigDecimal("12.30")))
        assertEquals(account.hashCode(), Account(number, BigDecimal("12.30")).hashCode())
        assertFailsWith<IllegalArgumentException> { Account(number, BigDecimal("-0.01")) }
        assertFailsWith<IllegalArgumentException> { Account(number, BigDecimal("1.230")) }
    }

    @Test
    fun `transfer validates and normalizes amount while allowing self transfer construction`() {
        val number = number("1111111111111111")
        val transfer = Transfer(number, number, BigDecimal("1.2"))
        assertMoney("1.20", transfer.amount)
        assertEquals(2, transfer.amount.scale())
        assertEquals(transfer, Transfer(number, number, BigDecimal("1.20")))
        assertEquals(transfer.hashCode(), Transfer(number, number, BigDecimal("1.20")).hashCode())
        assertFailsWith<IllegalArgumentException> { Transfer(number, number, BigDecimal.ZERO) }
        assertFailsWith<IllegalArgumentException> { Transfer(number, number, BigDecimal("-1.00")) }
        assertFailsWith<IllegalArgumentException> { Transfer(number, number, BigDecimal("1.230")) }
    }

    private fun number(value: String) = AccountNumber(value)

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertTrue(BigDecimal(expected).compareTo(actual) == 0, "Expected $expected but was $actual")
    }
}
