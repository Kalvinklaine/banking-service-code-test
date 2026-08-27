package banking

import java.math.BigDecimal

class Transfer(from: AccountNumber, to: AccountNumber, amount: BigDecimal) {
    val from: AccountNumber = from
    val to: AccountNumber = to
    val amount: BigDecimal

    init {
        require(amount.scale() <= 2) { "Transfer amount must have at most two decimal places" }
        require(amount > BigDecimal.ZERO) { "Transfer amount must be positive" }
        this.amount = amount.setScale(2)
    }

    override fun equals(other: Any?): Boolean =
        (this === other) || ((other is Transfer) && (from == other.from) && (to == other.to) && (amount == other.amount))

    override fun hashCode(): Int = 31 * (31 * from.hashCode() + to.hashCode()) + amount.hashCode()

    override fun toString(): String = "Transfer(from=$from, to=$to, amount=$amount)"
}
