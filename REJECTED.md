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

## Criterion 2 — Accepted under the documented interpretation

E7 causes one overdraft fee on Day 2.

The specification is ambiguous about whether a late back-valued command should
trigger retrospective fees for every subsequently affected closing day. This
implementation reconciles only the processed financial command's value day.
See `AMBIGUITIES.md` §1.

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
values. The net monetary projection can return, but append-only history cannot:

```text
E7                       -620.00
OVERDRAFT_FEE             -25.00
E9 REVERSAL               620.00
OVERDRAFT_FEE_REVERSAL     25.00
```

E7, its fee, E9, and the fee reversal remain permanently recorded. Therefore
balances and net fees may reconcile to their earlier values, while ledger and
fee history do not return to their pre-E7 state.

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
