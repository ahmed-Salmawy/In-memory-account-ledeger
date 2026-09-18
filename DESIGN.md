# In-Memory Account Ledger Core --- Design Notes

## Purpose

The implementation models a small financial ledger over Day 1--Day 6. It
prioritizes correctness, auditability, deterministic replay, explicit
monetary precision, and clear separation between booked money and
temporary authorization holds.

## Fundamental Concepts

### Ledger

The ledger is the append-only financial book of record. A ledger entry
represents money that has actually been booked.

Entries are never updated or deleted.

Corrections are represented by additional compensating entries.

### Ledger Balance

The closing ledger balance for a day is derived from the opening balance
plus all booked entries whose `value_date` is less than or equal to that
day.

### Available Balance

Available balance represents spendable funds:

``` text
available balance =
ledger balance - active holds
```

This is deliberately not stored as authoritative state.

### Authorization

An authorization reserves spending capacity.

Approval rule:

``` text
available balance after new hold >= 0
```

An approved authorization creates an active hold but no ledger debit.

### Settlement

Settlement converts an approved authorization into a booked financial
debit and releases the authorization hold.

A settlement referencing an unknown authorization is rejected and
produces no ledger movement.

### Reversal

A reversal never modifies the original entry.

``` text
Original:  -620.00
Reversal:  +620.00
Net:          0.00
```

The history therefore remains auditable.

## Input Events

  Event   Posted   Type            Account           Amount Value Date   Reference
  ------- -------- --------------- --------- -------------- ------------ ---------------
  E1      D1       CREDIT          ACC-001     AED 1,200.00 D1           
  E2      D1       DEBIT           ACC-001       AED 950.00 D1           
  E3      D2       AUTHORIZATION   ACC-001       AED 200.00 D2           Auth-A
  E4      D3       CREDIT          ACC-001       AED 400.00 D3           
  E5      D4       SETTLEMENT      ACC-001       AED 185.00 D4           Auth-A
  E6      D4       SETTLEMENT      ACC-001       AED 180.00 D4           Auth-Z
  E7      D5       DEBIT           ACC-001       AED 620.00 D2           back-valued
  E8      D5       AUTHORIZATION   ACC-001        AED 90.00 D5           Auth-B
  E9      D6       REVERSAL        ACC-001       reverse E7 D2           E7
  E10     D5       CREDIT          ACC-002       BHD 10.000 D5           3 instalments

## Event Time vs Value Time

`postedDay` describes when an event enters the replay stream.

`valueDay` describes when its financial effect belongs in the ledger
timeline.

E7 is the primary example:

``` text
postedDay = Day 5
valueDay  = Day 2
amount    = -620 AED
```

Therefore, once E7 is known, a Day 2 balance viewed as of Day 5 includes
E7.

This distinction is central to the design.

## State Separation

The engine maintains conceptually separate structures:

``` text
accounts
ledger entries
authorizations
processing errors
```

This prevents temporary authorization state from contaminating the
financial ledger.

## Append-Only Guarantee

The following operations append records:

-   credit
-   debit
-   settlement
-   reversal
-   overdraft fee
-   fee reversal, if required by chosen policy
-   Day 6 interest capitalization

No financial operation rewrites history.

## Derived State

Balances are projections rather than stored facts.

This avoids synchronization bugs such as:

``` text
account.balance != sum(ledger)
```

The ledger remains the source of truth.

## Error Handling

Domain-invalid events are reported explicitly.

For example:

``` text
E6 -> UNKNOWN_AUTHORIZATION(Auth-Z)
```

The rejected event creates no financial ledger debit.

## Idempotency / Duplicate Protection

Generated financial effects should have stable logical identities.

Examples:

``` text
fee: ACC-001/D2/OVERDRAFT
reversal: E9 -> E7
settlement: E5 -> Auth-A
```

The engine must not generate the same financial effect twice during
recalculation.

## Reporting

For each Day 1--Day 6, print:

``` text
account
closing ledger balance
available balance
fee assessments/reversals
authorization states
daily interest accrual
errors
```

Day 6 additionally shows the single capitalized-interest ledger credit.

## Scope Discipline

This is intentionally an in-memory domain exercise. The design does not
introduce:

-   REST
-   Spring Boot
-   databases
-   Kafka
-   distributed locking
-   external caches
-   persistence abstractions

Those would obscure the domain behavior being evaluated.
