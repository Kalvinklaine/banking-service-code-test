# Banking Service

A Kotlin/JVM 21 command-line application that processes a company's end-of-day banking data. It loads opening account balances and a transactions CSV, then reports each transfer outcome and the final balances.

## Requirements

- JDK 21
- No separate Gradle installation is required; the Gradle wrapper is included.

## Build and test

```shell
./gradlew clean test
```

## Run

Run against the supplied [opening balances](./mable_account_balances.csv) and [transactions](./mable_transactions.csv) files:

```shell
./gradlew --quiet run --args="mable_account_balances.csv mable_transactions.csv"
```

On macOS or Linux, build and use the installed distribution with:

```shell
./gradlew installDist
./build/install/banking-service/bin/banking-service mable_account_balances.csv mable_transactions.csv
```

On Windows, use `gradlew.bat` and `build\install\banking-service\bin\banking-service.bat`.

```text
Usage: banking-service <balances.csv> <transactions.csv>
```

A successful run with the supplied files prints:

```text
Transfer results
1 1111234522226789 -> 1212343433335665 500.00 APPLIED
2 3212343433335755 -> 2222123433331212 1000.00 APPLIED
3 3212343433335755 -> 1111234522226789 320.50 APPLIED
4 1111234522221234 -> 1212343433335665 25.60 APPLIED

Final balances
1111234522226789 4820.50
1111234522221234 9974.40
2222123433331212 1550.00
1212343433335665 1725.60
3212343433335755 48679.50
```

## Input formats

Both files are UTF-8, headerless CSV files. Opening balances contain exactly:

```text
<16-digit-account>,<balance>
```

The transactions CSV contains exactly:

```text
<from-account>,<to-account>,<amount>
```

Account numbers must contain exactly 16 ASCII digits. Monetary values use plain decimal notation with at most two decimal places: balances must be non-negative and transfer amounts must be positive. An empty file is accepted, but a physical blank row is rejected.

Beyond that core fixture format, the application deliberately makes strict, fail-fast assumptions for unspecified CSV edge cases: fields are not trimmed, and quoting, empty or extra fields, scientific notation, and values with more than two decimal places are rejected. Duplicate accounts in the balances file are also rejected. Malformed CSV errors identify the 1-based physical line and describe the invalid value or row shape.

## Processing and errors

Transfers are processed in file order. Each accepted transfer updates both accounts atomically. A rejected transfer changes neither account, is included in the report, and processing continues with the next row. A transfer may leave the source balance at exactly zero. Self-transfers, unknown source accounts, unknown destination accounts, and insufficient funds are reported respectively as `SELF_TRANSFER`, `UNKNOWN_SOURCE_ACCOUNT`, `UNKNOWN_DESTINATION_ACCOUNT`, and `INSUFFICIENT_FUNDS`. All monetary arithmetic uses `BigDecimal`.

Malformed input fails fast and produces no report; business-level rejections produce a report and allow later transfers to continue.

Completed reports, including transfer rejections, are written to standard output. Usage, file access, and malformed-input errors are written to standard error without a stack trace.

For successful runs and handled usage or input failures, the application returns:

- `0`: processing completed, including runs containing business-level transfer rejections
- `1`: an expected input path, file read, security, or malformed CSV failure
- `2`: invalid command-line usage

## Tests

The test suite uses `kotlin.test` and covers three layers: domain validation and transfer processing, CSV parsing and malformed-input behavior, and end-to-end CLI/report behavior.

## Design notes

Domain models and transfer processing are separated from CSV and CLI concerns. The CSV layer validates textual syntax and row structure while adding line context; domain constructors enforce business invariants. A `LinkedHashMap` provides efficient account lookup while preserving balances-file order. Input is read line by line and converted into validated domain values. Report output is deterministic and intended for human review, not as a machine-readable API. No framework, repository layer, or persistence was introduced; none is needed for this file-based batch workflow.
