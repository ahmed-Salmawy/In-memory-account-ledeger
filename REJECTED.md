# Acceptance Criteria Review

This review follows the supplied criteria in their original order. “Rejected”
means the criterion conflicts with another explicit invariant; it does not mean
the scenario is ignored.

## Criterion 1 — Accepted

The Day 2 closing ledger balance evaluated at the end of Day 5, before the
overdraft fee, is AED -370.00:

```text
1200.00 - 950.00 - 620.00 = -370.00
```

E7 is posted on Day 5 but has Day 2 as its value day, so it participates in the
Day 2 value-date projection.

## Criterion 2 — Rejected

The non-negotiable rule assesses the AED 25 fee once per account per day when
that day's closing ledger balance, including all entries with
`value_date <= day`, is negative. After E7 arrives (posted D5, value date D2),
the implementation's day-by-day assessment — each closing balance including
prior-day fees but excluding the day's own fee — is:

```text
D1   250.00
D2  -370.00   negative, fee assessed
D3     5.00   positive after the Day 2 fee, no fee
D4  -180.00   negative, fee assessed
D5  -205.00   negative, fee assessed
```

A literal application therefore assesses fees on Days 2, 4, and 5 — not only
Day 2. Accepting the criterion would require overriding the explicit daily rule
with an unstated exception, so it conflicts with the specification. The
implementation follows the rule: a back-valued movement reconciles every day
from its value day through the latest processed day, so E7 assesses three fees,
and E9's reconciliation appends an `OVERDRAFT_FEE_REVERSAL` for each. See
`AMBIGUITIES.md` §1.

## Criterion 3 — Accepted

Auth-A was approved for AED 200.00, so E5 may settle AED 185.00. The settlement
books AED -185.00, marks Auth-A settled, and releases its complete hold.

## Criterion 4 — Accepted

E6 references Auth-Z, which does not exist. The settlement is rejected with
`UNKNOWN_AUTHORIZATION` and creates no ledger movement.

## Criterion 5 — Accepted

An approved authorization reduces available balance without changing ledger
balance. The criterion is conditional: **if Auth-B is approved**.

In the supplied replay, Auth-B is declined because E8 has insufficient
available funds. That outcome does not invalidate the criterion's statement
about how an approved hold behaves.

## Criterion 6 — Rejected as written

The criterion says that after E9, all balances and fees return to their pre-E7
values. Balances and net fees do return, so the criterion is not rejected on
arithmetic:

```text
D2 balance   1200.00 - 950.00 - 620.00 + 620.00           =  250.00
net fees     -25.00 -25.00 -25.00 +25.00 +25.00 +25.00    =    0.00
```

Authorization state does not return. E8 requested a AED 90.00 hold on Day 5 and
was declined because E7 had already driven available funds negative. Without
E7, the Day 5 closing balance is AED 465.00 and Auth-B would have been
approved. E9 restores the balance, but the decision stands: approval is
evaluated once against the funds visible at that moment and is never re-run by
a later movement (AMBIGUITIES §2). The Day 6 report still reads:

```text
authorizations={Auth-A=SETTLED, Auth-B=DECLINED}
```

A reversal cannot un-decline an authorization. "All balances and fees return"
is therefore true of the numbers and false of the ledger's actual state, and a
customer who was refused a payment on Day 5 stays refused.

History does not return either. E7, its three fees, E9, and the three fee
reversals remain permanently recorded, because corrections are new entries
rather than deletions:

```text
E7                          -620.00
OVERDRAFT_FEE x3             -75.00   (D2, D4, D5)
E9 REVERSAL                  620.00
OVERDRAFT_FEE_REVERSAL x3     75.00   (D2, D4, D5)
```

## Criterion 7 — Rejected

Three BHD 3.334 instalments create money:

```text
3.334 + 3.334 + 3.334 = 10.002
```

The implementation preserves the BHD 10.000 source total using deterministic
minor-unit allocation:

```text
3.334 + 3.333 + 3.333 = 10.000
```

E10's own description carries the same defect: it asks for "three equal
instalments" of BHD 10.000. No three equal amounts at BHD's three-decimal
precision sum to 10.000, so equality and an exact total cannot both hold. The
exact total wins, because inventing BHD 0.002 is the one outcome a ledger may
never produce. See AMBIGUITIES §5.

## Criterion 8 — Rejected

The requirement states that rounded daily interest accruals must sum exactly
to the capitalized total. Discarding a rounding remainder would violate that
invariant.

The implementation therefore uses:

```text
capitalized interest = sum(rounded daily accruals)
```

No remainder is discarded.

## Rejected implementation approaches

- Floating-point money and silent input rounding can alter monetary values;
  use exact `BigDecimal` input and explicit `HALF_EVEN` calculated rounding.
- Mutable authoritative balances can diverge from ledger history; derive them
  from immutable opening values, append-only entries, and active holds.
- Sorting commands by posted day changes the supplied processing order.
- Frameworks, storage, external messaging, and web layers are outside this
  in-memory assessment.
