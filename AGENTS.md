# Repository Guidelines

## Project Structure & Module Organization

This repository is currently design-first: `DESIGN.md` defines ledger behavior, `IMPLEMENTATION_PLAN.md` describes the proposed Java components and delivery order, and `AMBIGUITIES.md` records deterministic decisions where requirements conflict. Read all three before changing domain behavior.

When implementation begins, use the standard Java layout: production code under `src/main/java`, grouped into `domain`, `service`, and `report`; tests belong under `src/test/java` with matching packages. Keep the project in-memory—REST APIs, databases, messaging, and persistence abstractions are explicitly out of scope.

## Build, Test, and Development Commands

No build configuration or executable source exists yet, so there are currently no build or test commands. Do not document or depend on a command until its build file is committed. Once Maven or Gradle is selected, update this section with the canonical commands (for example, `./mvnw test` or `./gradlew test`) and commit the wrapper so contributors use the same tool version.

Use `rg --files` to inspect tracked project files and `rg "term"` to locate related rules before editing them.

## Coding Style & Naming Conventions

Use plain Java and the JDK before adding dependencies. Indent with four spaces. Name classes in `PascalCase`, methods and fields in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Model money with `BigDecimal`, explicit currency scale, and `RoundingMode.HALF_EVEN`; never use floating-point types. Keep ledger entries immutable and append-only, and derive balances instead of storing mutable balance fields.

## Testing Guidelines

Use JUnit. Name test classes `*Test` and describe behavior in method names, such as `unknownAuthorizationDoesNotCreateDebit`. Cover currency precision, authorization holds, settlement validation, reversals, back-valued events, fee idempotency, interest reconciliation, and deterministic replay. Every defect fix should include the smallest regression test that fails without the fix.

## Commit & Pull Request Guidelines

No Git history is available in this directory, so no existing commit convention can be inferred. Use short, imperative subjects such as `Add authorization settlement validation`. Pull requests should summarize the behavioral change, reference the relevant design or ambiguity section, list tests run, and call out any changed accounting assumption. Include screenshots only when report formatting changes.
