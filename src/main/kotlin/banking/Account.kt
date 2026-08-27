package banking

import java.math.BigDecimal

class Account(number: AccountNumber, balance: BigDecimal) {
    val number: AccountNumber = number
    val balance: BigDecimal

    init {
        require(balance.scale() <= 2) { "Account balance must have at most two decimal places" }
        require(balance >= BigDecimal.ZERO) { "Account balance cannot be negative" }
        this.balance = balance.setScale(2)
    }

    override fun equals(other: Any?): Boolean =
        (this === other) || ((other is Account) && (number == other.number) && (balance == other.balance))

    override fun hashCode(): Int = 31 * number.hashCode() + balance.hashCode()

    override fun toString(): String = "Account(number=$number, balance=$balance)"
}
