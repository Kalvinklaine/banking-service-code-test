package banking.csv

class CsvInputException(
    val lineNumber: Int,
    reason: String,
    cause: Throwable? = null,
) : IllegalArgumentException("Line $lineNumber: $reason", cause)
