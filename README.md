# In-Memory Account Ledger

Milestone 1 implements exact money, immutable domain records, CREDIT/DEBIT
posting, and derived ledger balances. Authorization, settlement, reversal,
fee reconciliation, interest, allocation, and E1–E10 reporting are later
milestones; there is no application entry point yet.

## Build and test

Requires JDK 17 or newer. The Maven wrapper pins Maven 3.9.11; JUnit is
the only direct dependency and is test-scoped. No runtime dependencies.

```sh
./mvnw test                 # Monetary and ledger tests
./mvnw clean verify         # Clean compile, tests, and library JAR
./mvnw -o test              # Offline, with Maven/dependencies already cached
```

Use `mvnw.cmd` on Windows. First use requires network access if the Maven
distribution or dependencies are missing. Build output is ignored under
`target/`; test results are in `target/surefire-reports/`.

The standard Takari 0.5.5 wrapper scripts/JAR were reused from the locally
installed VS Code Maven extension because downloads are unavailable in this
environment. The wrapper JAR is a vendored bootstrap dependency, not project
build output. Its Apache license headers are retained.

## Structure

- `src/main/java/ledger/domain/`: Currency, Money, Account, LedgerEvent,
  EventType, LedgerEntry, and LedgerEntryType.
- `src/main/java/ledger/service/LedgerEngine.java`: posting, duplicate
  protection, immutable entry snapshots, and value-day balance queries.
- `src/test/java/ledger/`: matching domain/service tests.
- `DESIGN.md`, `IMPLEMENTATION_PLAN.md`, and `AMBIGUITIES.md`: architecture
  context and explicit policy decisions.
- `NUMBERS.md`, `REJECTED.md`, and `WORKLOG.md`: numeric choices,
  conflicting criteria/rejected approaches, and actual implementation record.

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
8. Events are processed in caller order. The engine is single-threaded.

Balance projection is a linear scan, appropriate for this small assessment.
The Ponytail approach keeps it in LedgerEngine until reuse or measured
replay size warrants extraction/indexing.

## First-milestone review issues

E7 produces negative principal-only balances on D2 and D4–D5, contradicting
the single-Day-2-fee criterion. E8 cannot approve Auth-B against negative
available funds. AMBIGUITIES §17 records both for review before those
features are built. BHD overdraft policy, capitalization per account, and
historical reporting semantics also require later decisions.

## Commit handoff in this environment

The workspace has no existing Git repository and prevents creation of
`.git`. The milestone commit is therefore made in an isolated temporary
checkout and exported to `milestone-1.bundle` beside this README. The bundle
contains complete Git history; it is not itself tracked. In a writable
location, restore it with:

```sh
git clone /absolute/path/to/milestone-1.bundle ledger-review
```

This preserves the actual commit instead of squashing or reconstructing it.
