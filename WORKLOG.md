# Implementation Worklog

All timestamps are UTC. Entries record completed implementation and verification
milestones.

## 2026-09-18T06:09:51Z — Core ledger completed

- Added exact AED/BHD money handling with explicit `HALF_EVEN` calculated
  rounding and rejection of input precision loss.
- Added immutable accounts and append-only credit/debit entries with derived
  value-day balances.
- Added account, currency, amount, identifier, and idempotency validation.
- Verification: **31 tests, 0 failures, 0 errors**.

## 2026-09-18T06:31:40Z — Authorization holds completed

- Added immutable approved/declined authorization records and available-balance
  projection without booking holds into the ledger.
- Added duplicate-authorization protection, retry behavior, account isolation,
  and back-valued balance coverage.
- Verification: **45 tests, 0 failures, 0 errors**.

## 2026-09-18T06:35:14Z — Settlement completed

- Added settlement entries linked to approved authorizations.
- Released the complete hold after settlement, including unused authorized
  amount, and rejected unknown, declined, settled, cross-account, and
  over-capture requests before mutation.
- Verification: **57 tests, 0 failures, 0 errors**.

## 2026-09-18T06:59:26Z — Full replay completed

- Added append-only reversals and overdraft fee assessment/reversal on the
  processed command's value day.
- Added exact instalment allocation, daily interest, Day 6 capitalization,
  structured replay errors, daily reports, and the E1–E10 application.
- Verified deterministic replay, AED 466.03 and BHD 10.008 Day 6 balances,
  `UNKNOWN_AUTHORIZATION` for E6, and declined Auth-B.
- Verification: **63 tests, 0 failures, 0 errors, 1 documented skipped
  contradictory criterion**.

## 2026-09-18T13:35:02Z — Command and domain-event flow completed

- Split command processing into focused balance, authorization, transaction,
  fee, and interest classes while keeping flat `domain` and `service` packages.
- Renamed incoming work to `LedgerCommand`/`CommandType` and published typed,
  past-tense domain events only after state changes.
- Moved fee reconciliation behind the common movement event and isolated
  listener failures without rolling back committed ledger state.
- Verification: **68 tests, 0 failures, 0 errors, 1 documented skipped
  contradictory criterion**.

## 2026-09-18T13:41:56Z — Decision documentation reviewed

- Reduced `AMBIGUITIES.md` to six material accounting decisions.
- Removed duplicated milestone narration and retained the chosen fee,
  authorization, settlement, projection, numeric, and idempotency policies.

## 2026-09-18T13:48:24Z — Acceptance criteria reviewed

- Mapped `REJECTED.md` directly to all eight supplied acceptance criteria.
- Corrected criterion 5 as a conditional statement, documented the append-only
  limitation in criterion 6, and recorded the monetary conflicts in criteria 7
  and 8.
- Replaced the invalid Auth-B alternative test with the documented contradictory
  BHD 3.334-per-instalment test.

## 2026-09-18T14:00:41Z — Submission verification

- Reviewed project structure, commands, documentation references, and source
  terminology against the final implementation.
- Ran `./mvnw clean verify`: **68 tests, 0 failures, 0 errors, 1 documented
  skipped contradictory criterion; BUILD SUCCESS**.

## 2026-09-18T14:03:30Z — Contradictory criterion activated

- Replaced the disabled criterion 7 example with an active, inline-labelled
  failing test as required by the assessment.
- Ran `./mvnw test`: **68 tests, 1 intentional failure, 0 errors, 0 skipped**.
  The failure compares the criterion's 3.334/3.334/3.334 allocation with the
  design's exact 3.334/3.333/3.333 allocation.
