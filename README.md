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
./mvnw -o package -DskipTests   # Builds the JAR without the designed test failure
java -cp target/classes ledger.LedgerApplication
```

Use `mvnw.cmd` on Windows. First use requires network access if the Maven
distribution or dependencies are missing. Build output is ignored under
`target/`; test results are in `target/surefire-reports/`.

The suite intentionally contains one failing test for acceptance criterion 7.
It demonstrates that three BHD 3.334 instalments total BHD 10.002 and therefore
conflict with the exact BHD 10.000 source amount. See `REJECTED.md` criterion 7.

## Reading the output

`LedgerApplication` prints one line per account per day, Day 1 through Day 6:

```text
D6 ACC-001 ledger=AED 466.03 available=AED 466.03 interest=AED 0.19 fees=[] authorizations={Auth-A=SETTLED, Auth-B=DECLINED} errors=[]
```

| Field | Meaning |
| --- | --- |
| `D6 ACC-001` | Business day and account for this row. |
| `ledger` | Closing ledger balance: opening balance plus every known entry whose value day is on or before this day, including fees and capitalized interest. |
| `available` | Closing balance minus the holds that were approved **as of that day**, so a hold settled later still reduces the earlier day's figure. |
| `interest` | That day's accrual alone, rounded at currency precision. It is not booked on this day; the six accruals are booked once, as the Day 6 capitalization credit already inside `ledger`. |
| `fees` | Fee entries whose value day is this day — `OVERDRAFT_FEE`, `OVERDRAFT_FEE_REVERSAL`, or both when a fee was later reversed. Empty means no fee activity dated here. |
| `authorizations` | Every authorization on the account created on or before this day, with the status it held on this day. |
| `errors` | Stable rejection codes for commands **posted** on this day. A rejected command books nothing. |

Two fields are dated differently on purpose: `fees` and `ledger` follow value
day, while `errors` follows posted day, because a rejection is an event in
processing time that never reaches the ledger timeline.

Reading Day 2 across the run shows the back-valued case: E7 arrives on Day 5
with a Day 2 value date, so the Day 2 row ends the replay showing the fee it
caused and the reversal E9 produced.

## Fee reconciliation walkthrough

`fee-reconciliation.html` is a self-contained page — no build step, no network,
open it in a browser — that steps through E1–E10 and shows why the back-valued
E7 produces three fees rather than one. It replays the stream command by
command, showing the pre-fee balance behind each decision, and a sandbox mode
accepts a hand-written command so the four-way per-day rule and the
latest-processed-day bound can be probed directly.

Its model is a JavaScript reimplementation of the fee semantics, kept
deliberately narrow: fee reconciliation, authorization holds, reversal, and
rejection only. Interest is not accrued and balances therefore exclude the Day 6
capitalization. Within that scope it agrees with the engine exactly — the same
fifteen entries in the same booking order, with the same entry IDs, value days,
and signs, and the same `UNKNOWN_AUTHORIZATION` rejection for E6.

The page is a teaching aid, not a second source of truth. `LedgerEngine` and the
test suite decide behaviour; if the two ever disagree, the page is wrong.

## Structure

- `src/main/java/ledger/domain/`: accounts, money, entries, authorizations,
  domain enums, exceptions, and the command payload. Immutable values only;
  nothing here depends on the service layer.
- `src/main/java/ledger/service/command/`: one typed handler per command type,
  routing, and the append-only entry book.
- `src/main/java/ledger/service/daily/`: calendar-driven postings — overdraft
  fee reconciliation and interest accrual with capitalization.
- `src/main/java/ledger/service/`: engine, context and wiring, replay, and
  balance projections.
- `src/main/java/ledger/report/`: immutable daily and replay reports, including
  captured replay errors.
- `src/main/java/ledger/LedgerApplication.java`: runnable E1–E10 scenario.
- `src/test/java/ledger/`: matching domain/service tests.
- `fee-reconciliation.html`: interactive walkthrough of the fee rule; see above.
- `DESIGN.md`, `IMPLEMENTATION_PLAN.md`, and `AMBIGUITIES.md`: architecture
  context and explicit policy decisions.
- `NUMBERS.md` and `REJECTED.md`: numeric choices and acceptance decisions.
- `PRODUCTION_CONSIDERATIONS.md`: behaviour at scale, value-dating consequences,
  authorization lifecycle, regulatory obligations, and what was deliberately cut.
- `WORKLOG.md`: timestamped implementation and verification milestones.

## Enforced invariants

1. Money always has the account currency's scale: AED 2, BHD 3. Input
   precision loss is rejected; calculated rounding explicitly uses HALF_EVEN.
2. Account currency is explicit configuration, validated at construction to
    match the immutable opening balance's currency.
3. Input CREDIT/DEBIT amounts are positive. Credits book positive entries;
   debits book negative entries and may overdraw an account.
4. Financial entries are immutable and append-only. `entries()` returns an
   unmodifiable snapshot; callers cannot rewrite or remove history.
5. `balance(accountId, day)` derives opening balance plus all currently
   known entries for that account with `valueDay <= day`. No balance cache
   or posted-day snapshot is authoritative.
6. Unknown accounts, currency mismatches, blank IDs, nonpositive days, and a
    value day later than its posted day are rejected. Only back-valued and
    same-day postings exist; forward value dating is out of scope, so every
    entry is visible to balances as soon as it is processed. Invalid transfers
    do not consume IDs or move money.
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
14. Fee reconciliation reevaluates every business day from a movement's value
    day through the engine's latest processed day — the maximum posted day
    processed so far. Caller-ordered posted days need not be monotonic, so the
    latest processed day, not the current command's posted day, bounds
    reconciliation. Replay extends the latest processed day through the
    report's final day before interest capitalization.
    Each day whose pre-fee closing balance is
    negative — prior-day fees included, the day's own fee or reversal
    excluded — receives AED 25 once; a later correction appends a fee reversal
    at the original fee's value day. No AED fee is invented for BHD accounts.
15. Daily positive-balance interest is rounded HALF_EVEN at account currency
    precision. Day 6 appends one capitalization entry per account, equal to
    the sum of its six rounded daily accruals.
16. Instalment allocation preserves exact minor units. E10 becomes BHD 3.334,
    3.333, and 3.333. Replay catches business rejections as ProcessingError
    records and produces immutable daily reports.
17. `LedgerEngine` routes validated commands through one
    `LedgerCommandDispatcher`, which resolves the `LedgerCommandHandler` that
    declared that command type. A handler returns the entries it booked, and an
    empty result — an authorization reserves funds without booking money — is
    what tells the engine no fee reconciliation is owed. Fee reconciliation and
    interest remain separate calendar-driven processors, invoked directly. No
    in-process event bus is used: a publisher with no subscriber would be the
    kind of machinery `DESIGN.md` excludes by name.

For example, after funding ACC-001 with AED 250:

```java
engine.process(new LedgerCommandPayload("E3", 2, 2, CommandType.AUTHORIZATION,
        "ACC-001", Money.of(Currency.AED, "200"), "Auth-A"));
engine.authorizations();                   // Auth-A: APPROVED
engine.balance("ACC-001", 2);              // AED 250.00
engine.availableBalance("ACC-001", 2);     // AED 50.00
engine.process(new LedgerCommandPayload("E5", 4, 4, CommandType.SETTLEMENT,
        "ACC-001", Money.of(Currency.AED, "185"), "Auth-A"));
engine.authorizations();                   // Auth-A: SETTLED
engine.balance("ACC-001", 4);              // AED 65.00
engine.availableBalance("ACC-001", 4);     // AED 65.00; full hold released
```

Balance projection is a linear scan, appropriate for this small assessment.
`AccountBalanceCalculator` owns the projection; an index is only needed when
measured replay size warrants it.

## Resolved interpretation conflicts

The selected fee policy applies the daily rule literally: a back-valued
movement reconciles through its posted day, so E7 assesses Day 2, Day 4, and
Day 5 fees, and E9 later appends all three reversals. The acceptance criterion
expecting exactly one Day 2 fee is rejected; see `REJECTED.md` criterion 2.
Auth-B is declined because E8 has insufficient available
funds. BHD has no AED-denominated fee, interest capitalizes per account, and
daily reports reconstruct authorization status by day. See AMBIGUITIES §§1–5.
