# In-Memory Account Ledger

The complete in-memory ledger implements exact money, immutable append-only
entries, authorization holds, settlement, reversal, overdraft fee
reconciliation, interest capitalization, exact instalment allocation, and
Day 1–6 reporting for E1–E10.

## Build and test

Requires JDK 17 or newer. The Maven wrapper pins Maven 3.9.11; JUnit is
the only direct dependency and is test-scoped. No runtime dependencies.

```sh
./mvnw test                 # Runs the suite; see expected failure below
./mvnw clean verify         # Clean build; reports the same designed failure
./mvnw -o test              # Offline, with Maven/dependencies already cached
./mvnw -o package
java -cp target/classes ledger.LedgerApplication
```

Use `mvnw.cmd` on Windows. First use requires network access if the Maven
distribution or dependencies are missing. Build output is ignored under
`target/`; test results are in `target/surefire-reports/`.

The suite intentionally contains one failing test for acceptance criterion 7.
It demonstrates that three BHD 3.334 instalments total BHD 10.002 and therefore
conflict with the exact BHD 10.000 source amount. See `REJECTED.md` criterion 7.

## Structure

- `src/main/java/ledger/domain/`: accounts, money, commands, entries,
  authorizations, business errors, and post-processing domain events.
- `src/main/java/ledger/service/`: `LedgerEngine`, replay, balance and business
  processors, and synchronous event delivery.
- `src/main/java/ledger/report/`: immutable daily and replay reports.
- `src/main/java/ledger/LedgerApplication.java`: runnable E1–E10 scenario.
- `src/test/java/ledger/`: matching domain/service tests.
- `DESIGN.md`, `IMPLEMENTATION_PLAN.md`, and `AMBIGUITIES.md`: architecture
  context and explicit policy decisions.
- `NUMBERS.md` and `REJECTED.md`: numeric choices and acceptance decisions.
- `WORKLOG.md`: timestamped implementation and verification milestones.

## Enforced invariants

1. Money always has the account currency's scale: AED 2, BHD 3. Input
   precision loss is rejected; calculated rounding explicitly uses HALF_EVEN.
2. Opening money is immutable. Account currency is derived from it.
3. Input CREDIT/DEBIT amounts are positive. Credits book positive entries;
   debits book negative entries and may overdraw an account.
4. Financial entries are immutable and append-only. `entries()` returns an
   unmodifiable snapshot; callers cannot rewrite or remove history.
5. `balance(accountId, day)` derives opening balance plus all currently
   known entries for that account with `valueDay <= day`. No balance cache
   or posted-day snapshot is authoritative.
6. Unknown accounts, currency mismatches, blank IDs, and nonpositive days
   are rejected. Invalid transfers do not consume IDs or move money.
7. Event IDs are unique across the engine. Identical retries are no-ops;
   conflicting payloads using the same ID are rejected.
8. Commands are processed in caller order. The engine is single-threaded.
9. Approved authorizations reserve funds without booking money. Insufficient
   funds produce a retained DECLINED record; exactly sufficient funds approve.
   Identical retries preserve either decision. Holds do not expire.
10. `availableBalance(accountId, day)` subtracts all current approved holds
    from `balance(accountId, day)`. This is not a historical hold snapshot.
    Authorization approval uses the request's posted day, not its value day.
11. Authorization IDs are globally unique, including declined requests.
    Duplicates raise `LedgerValidationException` with `DUPLICATE_AUTHORIZATION_ID`.
    `authorizations()` returns an immutable snapshot in processing order.
12. A settlement requires an approved authorization for the same account and
    currency, with a positive amount no greater than the authorized amount.
    It posts one debit linked to the authorization, marks it SETTLED, and
    releases the full hold. Identical retries are no-ops; a second capture
    is rejected. Invalid settlements leave entries and holds unchanged.
13. A reversal appends the exact opposite of every booked entry from its
    referenced event. Unknown, cross-account, repeated, and nonfinancial
    references are rejected without movement.
14. Fee reconciliation examines only the financial command's value day. A
    negative AED pre-fee balance receives AED 25 once; a later correction
    appends a fee reversal. No AED fee is invented for BHD accounts.
15. Daily positive-balance interest is rounded HALF_EVEN at account currency
    precision. Day 6 appends one capitalization entry per account, equal to
    the sum of its six rounded daily accruals.
16. Instalment allocation preserves exact minor units. E10 becomes BHD 3.334,
    3.333, and 3.333. Replay catches business rejections as ProcessingError
    records and produces immutable daily reports.
17. Successful commands publish typed domain events after their state changes.
    Fee reconciliation subscribes to all completed financial movements.
    Rejections and new interest capitalizations are also published. Delivery is
    synchronous and ordered; listener failures are recorded without rolling
    back committed ledger state or blocking later listeners.

For example, after funding ACC-001 with AED 250:

```java
engine.process(new LedgerCommand("E3", 2, 2, CommandType.AUTHORIZATION,
        "ACC-001", Money.of(Currency.AED, "200"), "Auth-A"));
engine.authorizations();                   // Auth-A: APPROVED
engine.balance("ACC-001", 2);              // AED 250.00
engine.availableBalance("ACC-001", 2);     // AED 50.00
engine.process(new LedgerCommand("E5", 4, 4, CommandType.SETTLEMENT,
        "ACC-001", Money.of(Currency.AED, "185"), "Auth-A"));
engine.authorizations();                   // Auth-A: SETTLED
engine.balance("ACC-001", 4);              // AED 65.00
engine.availableBalance("ACC-001", 4);     // AED 65.00; full hold released
```

Balance projection is a linear scan, appropriate for this small assessment.
`AccountBalanceCalculator` owns the projection; an index is only needed when
measured replay size warrants it.

## Resolved interpretation conflicts

The selected fee policy reconciles only the processed command's value day, so
E7 assesses one Day 2 fee and does not propagate fees to Days 4–5. E9 later
appends its reversal. Auth-B is declined because E8 has insufficient available
funds. BHD has no AED-denominated fee, interest capitalizes per account, and
daily reports reconstruct authorization status by day. See AMBIGUITIES §§1–5.
