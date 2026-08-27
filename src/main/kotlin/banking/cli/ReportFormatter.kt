package banking.cli

import banking.Account
import banking.AccountNumber
import banking.Transfer
import banking.TransferResult

object ReportFormatter {
    fun format(
        transfers: List<Transfer>,
        results: List<TransferResult>,
        finalBalances: Map<AccountNumber, Account>,
    ): String {
        require(transfers.size == results.size) {
            "Transfer and result counts must match"
        }

        return buildString {
            append("Transfer results\n")
            transfers.indices.forEach { index ->
                val transfer = transfers[index]
                append(index + 1)
                append(' ')
                append(transfer.from)
                append(" -> ")
                append(transfer.to)
                append(' ')
                append(transfer.amount.toPlainString())
                append(' ')
                when (val result = results[index]) {
                    TransferResult.Applied -> append("APPLIED")
                    is TransferResult.Rejected -> append("REJECTED ").append(result.reason)
                }
                append('\n')
            }
            append("\nFinal balances\n")
            finalBalances.forEach { (number, account) ->
                append(number)
                append(' ')
                append(account.balance.toPlainString())
                append('\n')
            }
        }
    }
}
