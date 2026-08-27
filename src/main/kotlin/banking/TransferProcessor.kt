package banking

class TransferProcessor(initialAccounts: Map<AccountNumber, Account>) {
    private val accounts = LinkedHashMap<AccountNumber, Account>()

    init {
        initialAccounts.forEach { (number, account) ->
            require(number == account.number) { "Account map key must match account number" }
            accounts[number] = account
        }
    }

    fun process(transfer: Transfer): TransferResult {
        if (transfer.from == transfer.to) {
            return TransferResult.Rejected(TransferRejectionReason.SELF_TRANSFER)
        }
        val source = accounts[transfer.from]
            ?: return TransferResult.Rejected(TransferRejectionReason.UNKNOWN_SOURCE_ACCOUNT)
        val destination = accounts[transfer.to]
            ?: return TransferResult.Rejected(TransferRejectionReason.UNKNOWN_DESTINATION_ACCOUNT)
        if (source.balance < transfer.amount) {
            return TransferResult.Rejected(TransferRejectionReason.INSUFFICIENT_FUNDS)
        }

        val updatedSource = Account(source.number, source.balance - transfer.amount)
        val updatedDestination = Account(destination.number, destination.balance + transfer.amount)
        accounts[transfer.from] = updatedSource
        accounts[transfer.to] = updatedDestination

        return TransferResult.Applied
    }

    fun processAll(transfers: Iterable<Transfer>): List<TransferResult> = transfers.map(::process)

    fun snapshot(): Map<AccountNumber, Account> = LinkedHashMap(accounts)
}
