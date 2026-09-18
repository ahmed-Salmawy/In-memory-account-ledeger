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
  Added findings and choices in AMBIGUITIES §§17–22 and REJECTED.md.
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

## Decisions awaiting review

- Daily overdraft rule versus the single-Day-2-fee criterion.
- Negative available balance at E8 versus the requirement for active Auth-B.
- AED fee treatment for BHD and whether capitalization means one per account.
- Historical report/hold snapshots versus current-known value-day projections.
- The planned deliberately failing interpretation test stays deferred until
  the relevant policy is selected; this milestone's suite passes normally.
