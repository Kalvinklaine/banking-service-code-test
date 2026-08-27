package banking

enum class TransferRejectionReason {
    SELF_TRANSFER,
    UNKNOWN_SOURCE_ACCOUNT,
    UNKNOWN_DESTINATION_ACCOUNT,
    INSUFFICIENT_FUNDS,
}

sealed interface TransferResult {
    data object Applied : TransferResult

    data class Rejected(val reason: TransferRejectionReason) : TransferResult
}
