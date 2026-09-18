# Repository Guidelines

## Project Structure & Module Organization

This repository contains the complete in-memory ledger implementation. `DESIGN.md` defines ledger behavior, `IMPLEMENTATION_PLAN.md` records the delivery plan, and `AMBIGUITIES.md` records deterministic decisions where requirements conflict. Read all three before changing domain behavior.

Production code uses the standard Java layout under `src/main/java`. Keep domain types flat under `ledger/domain` and service classes flat under `ledger/service`; output lives under `ledger/report`. `LedgerEngine` orchestrates the focused processor classes without adding one-class subpackages. Tests belong under `src/test/java` with matching packages. Keep the project in-memory—REST APIs, databases, external messaging, and persistence abstractions are explicitly out of scope.

## Build, Test, and Development Commands

Use the committed Maven wrapper so contributors share Maven 3.9.11:

```sh
./mvnw test
./mvnw clean verify
./mvnw -o test              # when the Maven distribution and dependencies are cached
./mvnw -o package
java -cp target/classes ledger.LedgerApplication
```

Use `rg --files` to inspect tracked project files and `rg "term"` to locate related rules before editing them.

## Coding Style & Naming Conventions

Use plain Java and the JDK before adding dependencies. Indent with four spaces. Name classes in `PascalCase`, methods and fields in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Model money with `BigDecimal`, explicit currency scale, and `RoundingMode.HALF_EVEN`; never use floating-point types. Keep ledger entries immutable and append-only, and derive balances instead of storing mutable balance fields.

## Testing Guidelines

Use JUnit. Name test classes `*Test` and describe behavior in method names, such as `unknownAuthorizationDoesNotCreateDebit`. Cover currency precision, authorization holds, settlement validation, reversals, back-valued events, fee idempotency, interest reconciliation, and deterministic replay. Every defect fix should include the smallest regression test that fails without the fix.

## Commit & Pull Request Guidelines

Use short, imperative subjects such as `Add authorization settlement validation`. Pull requests should summarize the behavioral change, reference the relevant design or ambiguity section, list tests run, and call out any changed accounting assumption. Include screenshots only when report formatting changes.
