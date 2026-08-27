package banking

data class AccountNumber(val value: String) {
    init {
        require(value.length == 16 && value.all { it in '0'..'9' }) {
            "Account number must contain exactly 16 ASCII digits"
        }
    }

    override fun toString(): String = value
}
