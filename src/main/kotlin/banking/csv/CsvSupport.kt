package banking.csv

import java.math.BigDecimal

internal object CsvSupport {
    private val moneyPattern = Regex("-?[0-9]+(?:\\.[0-9]{1,2})?")

    fun fields(line: String, expectedCount: Int, lineNumber: Int): List<String> {
        val fields = buildList {
            var fieldStart = 0
            line.forEachIndexed { index, character ->
                if (character == ',') {
                    add(line.substring(fieldStart, index))
                    fieldStart = index + 1
                }
            }
            add(line.substring(fieldStart))
        }
        if (fields.size != expectedCount || fields.any(String::isEmpty)) {
            throw CsvInputException(lineNumber, "Expected exactly $expectedCount non-empty fields")
        }
        return fields
    }

    fun money(value: String, lineNumber: Int, fieldDescription: String): BigDecimal {
        val description = "Invalid $fieldDescription '$value'"
        if (!moneyPattern.matches(value)) {
            throw CsvInputException(lineNumber, description)
        }
        return wrap(lineNumber, description) { BigDecimal(value) }
    }

    fun <T> wrap(lineNumber: Int, description: String, operation: () -> T): T =
        try {
            operation()
        } catch (exception: IllegalArgumentException) {
            val reason = exception.message?.let { "$description: $it" } ?: description
            throw CsvInputException(lineNumber, reason, exception)
        }
}
