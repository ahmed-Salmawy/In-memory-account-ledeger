# Implementation Worklog

All timestamps below are UTC and were read from the execution environment.
Entries describe work actually performed; there is no reconstructed history
from the architecture discussion.

## 2026-09-18T06:09:51Z — First milestone implemented and verified

- Inspected the workspace and read AGENTS.md, IMPLEMENTATION_PLAN.md,
  DESIGN.md, and AMBIGUITIES.md completely before implementation. The
  workspace contained only these documents and no Git repository.
- Explained the proposed domain/service structure, invariants, and the
  fee-count/Auth-B contradictions before writing implementation code.
  Added implementation findings and choices to AMBIGUITIES.md and REJECTED.md.
- Chose Java release 17, Maven 3.9.11, and test-scoped JUnit 5.10.2.
  Reused a cached standard Maven wrapper because Maven Central DNS lookup
  failed. Verified the wrapper launches Maven 3.9.11.
- Wrote monetary and CREDIT/DEBIT tests first. The initial `mvn -o test`
  failed at test compilation because the implementation classes did not
  exist yet; this was the expected first step, not a passing test run.
- Added Currency, Money, Account, LedgerEvent, LedgerEntry, their two
  CREDIT/DEBIT enums, and LedgerEngine. Money uses exact input scale with
  explicit HALF_EVEN rounding for calculations. Opening balance is immutable
  account configuration; queries sum signed entries by account/value day.
- Added account/currency/amount validation, read-only entry snapshots,
  identical-event retry protection, and rejection of conflicting event IDs.
  Balance projection is an O(n) scan; no extracted balance service or cache
  was necessary. Kept all authorization/fee/interest/replay work deferred.
- `mvn -o test` on JDK 21 with release 17 completed at 06:06:14Z:
  **31 tests, 0 failures, 0 errors, 0 skipped** (18 Money, 13 LedgerEngine).
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
  ./mvnw -o clean verify` on JDK 17 completed at 06:08:44Z:
  **31 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**. A library
  JAR and Surefire reports were produced under ignored `target/`.
- Added README.md and NUMBERS.md with runnable commands, enforced
  invariants, milestone boundaries, numeric policies, and toolchain pins.
  Preserved the original DESIGN.md, IMPLEMENTATION_PLAN.md, and AGENTS.md.
- Workspace `git init` failed with `Operation not permitted` for `.git`.
  An isolated repository was successfully initialized at
  `/private/tmp/ledger-milestone-git.bvk0OQ`. The milestone will be committed
  there and exported as `milestone-1.bundle`, retaining actual Git history.
  Build output and the bundle itself are excluded from that commit.

## 2026-09-18T06:31:40Z — Authorization milestone implemented and verified

- Added immutable Authorization records with APPROVED/DECLINED statuses,
  authorization event references, and current available-balance projection.
  Approved holds reserve funds without ledger entries; declines are retained.
- Reused event deduplication for both decisions. Added the
  DUPLICATE_AUTHORIZATION_ID business error; rejected requests do not consume
  event or authorization IDs. Exposed immutable ordered authorization snapshots.
- Documented posted-day approval, current-hold query semantics, and precedence
  of the nonnegative-available approval rule over the conflicting Auth-B
  expectation now summarized in AMBIGUITIES §2. Settlement remained the next milestone.
- Added 13 authorization test cases covering exact funds/BHD precision,
  insufficient funds, retries, invalid input, duplicate IDs, account isolation,
  nonchronological requests, back-valued debits, no expiry, and deterministic replay.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
  ./mvnw -o test` completed on JDK 17: **45 tests, 0 failures, 0 errors,
  0 skipped; BUILD SUCCESS**. No new dependencies were added.
- Changes are in the workspace; the existing milestone-1 bundle is unchanged.

## 2026-09-18T06:35:14Z — Settlement milestone implemented and verified

- Added SETTLEMENT events/entries and SETTLED authorization status. Successful
  settlement books the requested debit, links it to the authorization, and
  releases the entire hold, including the unused portion of a smaller capture.
- Added business error codes for unknown authorizations, account mismatch,
  non-approved status, and over-capture. Validation precedes mutation, and
  rejected events remain retryable. Existing event deduplication prevents
  repeated captures; replaying the original authorization cannot reopen it.
- Documented settlement policy and current-state hold projection in
  AMBIGUITIES §3. Reversal was the next milestone; structured error collection
  remains part of reporting. No new dependencies were added.
- Added 12 settlement test cases covering smaller/exact captures, unknown or
  declined authorizations, account/currency mismatch, over-capture, retries,
  unchanged rejection state, BHD precision, snapshots, back-valued settlement,
  subsequent insufficient funds, validation, and deterministic replay.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
  ./mvnw -o test` completed on JDK 17: **57 tests, 0 failures, 0 errors,
  0 skipped; BUILD SUCCESS**. Changes remain in the workspace.

## 2026-09-18T06:59:26Z — Remaining milestones completed

- Added append-only event reversals with account/reference validation and
  exact compensation of allocated multi-entry events.
- Implemented the selected event-value-day fee policy: AED 25.00 on the
  processed negative day only, plus append-only fee reversal after correction.
  BHD accounts receive no AED fee because FX is out of scope.
- Added exact minor-unit allocation, daily positive-balance interest, one
  Day 6 capitalization per account, structured replay errors, immutable daily
  reports, and the runnable E1–E10 application.
- Full replay produces Day 6 AED 466.03 and BHD 10.008, reports E6 as
  UNKNOWN_AUTHORIZATION without movement, and declines Auth-B under the
  nonnegative-available rule. The conflicting active-Auth-B criterion remains
  as one explicitly disabled interpretation test.
- `./mvnw -o test`: **63 tests, 0 failures, 0 errors, 1 intentionally skipped**.

## Decisions awaiting production confirmation

- Whether a production product wants propagated historical daily fees instead
  of the selected event-value-day assessment.
- Whether BHD overdrafts should have a local-currency fee or an FX policy.

## 2026-09-18 — Domain package refactor

- Grouped the flat domain package into account, authorization, error, money,
  and transaction packages. Moved MoneyTest to its matching package and
  updated all application, service, report, and test imports without changing
  ledger behavior.

## 2026-09-18 — LedgerEngine responsibility refactor

- Reduced LedgerEngine to validation, idempotency, dispatch, and public query
  delegation. Extracted concrete account-balance, authorization, transaction,
  overdraft-fee, and interest processors into matching service subpackages.
- Reused the same in-memory maps and append-only entry list; no interfaces,
  factories, duplicate state, dependencies, or behavioral changes were added.

## 2026-09-18 — Internal domain events

- Added typed events for completed movements, authorization decisions, fees,
  interest capitalization, and business rejections. Events publish only after
  their state change; identical retries remain silent.
- Moved fee reconciliation behind the common movement subscription. Delivery
  is synchronous and ordered, with listener failures retained without undoing
  ledger state or blocking other listeners.
- Added five event-flow tests for settlement visibility, nested fee ordering,
  retry/rejection behavior, listener isolation, and interest idempotency.
- `./mvnw test`: **68 tests, 0 failures, 0 errors, 1 intentionally skipped**.

## 2026-09-18 — Command/event terminology

- Renamed incoming `LedgerEvent`/`EventType` to `LedgerCommand`/`CommandType`.
  Processors execute commands directly; past-tense domain events publish only
  after command processing changes state.

## 2026-09-18 — Package flattening

- Preserved every domain and processor class while removing one-class nested
  packages. Domain types now live in `ledger.domain`; engine, replay, event
  delivery, and processors live in `ledger.service`.
