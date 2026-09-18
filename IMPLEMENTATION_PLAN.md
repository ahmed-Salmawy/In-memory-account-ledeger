# In-Memory Account Ledger Core --- Implementation Plan

## 1. Goal

Build a deterministic, in-memory ledger engine that replays E1--E10 in
the supplied order and reports, per day:

-   closing ledger balance
-   available balance
-   overdraft fee assessments
-   authorization states
-   interest accrual
-   processing errors

No web layer, database, persistence, messaging infrastructure, or UI.

## 2. Core Design Principle

Separate **input events** from **derived financial state**.

``` text
Event Stream (E1..E10)
        |
        v
   Replay Engine
        |
        +--> Append-only Ledger Entries
        +--> Authorization/Hold State
        +--> Processing Errors
        |
        v
   Daily Projection / Report
```

The ledger is the immutable book of posted money movements.
Authorization holds are maintained separately because they affect
available balance but are not booked ledger money.

## 3. Domain Model

### Account

Keep the account intentionally small.

``` text
Account
- accountId
- openingBalance: Money
```

Derive currency from `openingBalance`. Do not store mutable ledger or available
balances in Account; derive them from the immutable opening balance, ledger
entries, and active holds.

### Currency / Money

Use `BigDecimal` with explicit currency scale and rounding policy.

-   AED: scale 2
-   BHD: scale 3

Never use `double` or `float`.

### LedgerCommand

Represents an input command from the supplied stream.

``` text
LedgerCommand
- eventId
- postedDay
- valueDay
- type
- accountId
- amount
- currency
- authorizationId?       // authorization/settlement
- referencedEventId?     // reversal
```

Command types:

``` text
CREDIT
DEBIT
AUTHORIZATION
SETTLEMENT
REVERSAL
```

### LedgerEntry

Represents an immutable booked financial movement.

``` text
LedgerEntry
- entryId
- sourceEventId
- accountId
- amount              // signed
- currency
- valueDay
- entryType
- referenceId?
```

Possible generated entry types include:

``` text
CREDIT
DEBIT
SETTLEMENT
REVERSAL
OVERDRAFT_FEE
OVERDRAFT_FEE_REVERSAL
INTEREST_CAPITALIZATION
```

### Authorization / Hold

Authorization state is separate from ledger entries.

``` text
Authorization
- authorizationId
- accountId
- amount
- currency
- createdDay
- status
```

Statuses:

``` text
APPROVED
DECLINED
SETTLED
```

No authorization expiry/TTL will be invented because the assessment
specifies none.

### ProcessingError

``` text
ProcessingError
- eventId
- day
- code
- message
```

Example: settlement references unknown `Auth-Z`.

## 4. Balance Rules

### Ledger Balance

For account A and day D:

``` text
opening balance
+ sum(booked ledger entries where valueDay <= D)
```

### Available Balance

``` text
ledger balance
- sum(active approved authorization holds)
```

An authorization changes available balance only.

A settlement releases its associated hold and creates a ledger debit.

## 5. Event Processing

### CREDIT

Validate account/currency/amount and append a positive ledger entry.

### DEBIT

Validate and append a negative ledger entry.

### AUTHORIZATION

1.  Calculate current available balance.
2.  Calculate available balance after proposed hold.
3.  Approve only if result is \>= 0.
4.  If approved, create active hold.
5.  Do not create a ledger entry.

### SETTLEMENT

1.  Find authorization by authorization ID.
2.  If absent, reject and record error.
3.  If valid, append settlement debit.
4.  Release/mark authorization as settled.
5.  Never debit funds for an unknown authorization.

### REVERSAL

1.  Locate referenced ledger event/entry.
2.  Never mutate or delete it.
3.  Append a compensating ledger entry with the opposite sign.
4.  Link the reversal to the original event.

Example:

``` text
E7  DEBIT      -620.00
E9  REVERSAL   +620.00  -> references E7
```

## 6. Back-Valued Events

E7 arrives on Day 5 but has value date Day 2.

After processing E7, recompute the affected value-date projection
starting at Day 2.

This means historical daily balances can change even though the ledger
remains append-only.

Fee behavior for such historical changes is an explicit design decision
documented in `AMBIGUITIES.md`.

## 7. Overdraft Fee

Rule:

``` text
AED 25.00 once per account per day
when closing ledger balance for that day is negative.
```

The fee itself is an immutable ledger entry with the same value day as
the assessment day.

Fee generation must be idempotent: replay/recalculation cannot create
duplicate fees for the same account/day/rule.

If the chosen policy determines that a previously assessed fee is no
longer valid after a back-valued correction, append a fee-reversal entry
rather than deleting the original fee.

## 8. Interest

For every day:

``` text
daily interest = positive closing ledger balance × 0.0004
```

No interest accrues on zero or negative balances.

Round each daily accrual to the account currency precision using the
documented rounding mode.

At the end of Day 6:

``` text
capitalized interest = exact sum of rounded daily accruals
```

Append one interest credit. Never discard a rounding residual.

## 9. BHD Instalment Allocation

E10 is BHD 10.000 split into three instalments.

The allocation must preserve the original amount exactly.

Example deterministic allocation:

``` text
3.334
3.333
3.333
-----
10.000
```

Do not create three `3.334` entries because that totals `10.002`.

## 10. Suggested Java Components

``` text
domain/
  Account.java
  Currency.java
  LedgerCommand.java
  CommandType.java
  LedgerEntry.java
  LedgerEntryType.java
  Authorization.java
  AuthorizationStatus.java
  ProcessingError.java

service/
  LedgerEngine.java
  BalanceCalculator.java
  FeeEngine.java
  InterestCalculator.java
  MoneyAllocator.java

report/
  DailyReport.java
  ReplayReport.java

test/
  LedgerReplayTest.java
```

Avoid unnecessary frameworks. Plain Java + JUnit is sufficient.

## 11. Processing Flow

``` text
for event in E1..E10:
    validate event
    process according to event type
    append financial entries where applicable
    update authorization state where applicable
    recompute affected daily projection when value dates are historical
    reconcile applicable fees

after event replay:
    calculate rounded daily interest
    capitalize interest once at end of Day 6
    print Day 1..Day 6 reports
```

## 12. Testing Strategy

Cover at minimum:

-   credit/debit ledger effects
-   authorization approval
-   authorization decline
-   hold affects available but not ledger balance
-   valid settlement
-   unknown authorization settlement rejection
-   back-valued debit
-   append-only reversal
-   overdraft fee idempotency
-   back-valued fee compensation policy
-   AED rounding
-   BHD rounding
-   BHD 10.000 exact three-way allocation
-   positive-only interest
-   exact daily-interest-to-capitalization reconciliation
-   approved holds affect available balance without changing ledger balance
-   deterministic replay

Also include the explicitly requested intentionally failing test,
clearly annotated with what unresolved/alternative interpretation it
demonstrates.

## 13. Implementation Order

1.  Money/currency rules.
2.  Account and event models.
3.  Append-only ledger.
4.  Balance calculation.
5.  Authorization lifecycle.
6.  Settlement validation.
7.  Reversal behavior.
8.  Back-valued projection.
9.  Fee assessment/reconciliation.
10. Interest calculation/capitalization.
11. BHD instalment allocation.
12. Daily reporting.
13. Full E1--E10 replay test.
14. Documentation and intentionally failing test.
