# Ambiguities and Decisions

This file records only specification gaps that materially changed accounting
behavior or acceptance results. Each decision is deterministic for the
assessment and would be confirmed with product or accounting stakeholders in
production.

## 1. Back-valued transactions and overdraft fees

**Conflict.** E7 arrives on Day 5 with Day 2 as its value day. Propagating its
effect through every later closing balance could create fees on several days,
while the acceptance criterion expects exactly one fee on Day 2. The
specification also does not say how a later correction affects an existing
fee.

**Decision.** A movement reconciles every business day from its value day
through the engine's latest processed day — the maximum posted day
successfully processed so far — so each closing balance it retroactively
affects is reevaluated against the non-negotiable daily rule. The latest
processed day, not the current command's posted day, bounds reconciliation,
because caller-ordered posted days need not be monotonic and a later-arriving
command can change an already-seen day's closing balance; rejected commands
never move it. The replay extends assessment through the report's final day
before interest capitalization. Fee eligibility
uses a pre-fee view of the target day: closing balances include prior-day fees
and interest but exclude the target day's own fee or reversal, so a fee can
never trigger itself. When reconciliation finds an active fee on a day that is
no longer negative, it appends an equal `OVERDRAFT_FEE_REVERSAL` at the
original fee's value day; nothing is edited or deleted.

A reversal compensates only the referenced command's booked movement. Fee
reconciliation then independently decides whether its derived fees remain
valid. Consequently, E7 assesses Day 2, Day 4, and Day 5 fees, and E9 causes
their three append-only reversals. The acceptance criterion expecting exactly
one Day 2 fee is rejected in `REJECTED.md`.

**Fees can chain, and this is a consequence of the decision rather than an
oversight.** A fee is a booked entry carrying a value day, so it counts toward
every later day's closing balance; only the assessed day's own fee is excluded,
otherwise a fee would trigger itself. An account left between zero and
AED 25.00 is therefore pushed negative by its own fee and assessed again the
next day. The supplied scenario comes within AED 5.00 of demonstrating it:

```text
D3 before the Day 2 fee    30.00
D3 after the Day 2 fee      5.00   positive, so no Day 3 fee
```

The alternative — testing eligibility against a balance that excludes every
fee rather than just the assessed day's — was rejected because the
non-negotiable rule defines the test on the closing ledger balance, and a
booked fee is part of that balance. Excluding fees would require an exception
the specification does not grant, and would also make a genuinely overdrawn
account look solvent.

In production this would need a bound: a cap per account per period, or an
explicit rule that a fee-induced overdraft is not itself fee-bearing.
Compounding penalty fees is a consumer-protection question, so the bound is a
product decision to be taken with the business rather than a default invented
by the ledger.

## 2. Authorization approval and Auth-B

**Gap.** The acceptance criterion explains the effect **if Auth-B is approved**
but does not require approval. The supplied movements leave insufficient
available funds for its AED 90 hold. No authorization expiry or retry policy is
specified.

**Decision.** At authorization time, use the ledger balance through the
command's posted day, subtract all currently approved holds, and approve only
when the remaining amount is non-negative. Retain declined attempts for audit
without creating a hold. Approved holds do not expire, and later back-valued
movements do not rewrite an earlier decision. An identical retry preserves the
original result; a new attempt needs new command and authorization IDs.

Under this rule Auth-B is `DECLINED`. The conditional acceptance criterion
remains valid for any authorization that is approved.

## 3. Settlement and hold release

**Gap.** Auth-A reserves AED 200 but settles for AED 185. The specification
does not define the unused AED 15, over-capture, or whether settlement should
repeat the funds check after an authorization was approved.

**Decision.** A successful settlement books only its requested amount, marks
the authorization `SETTLED`, and releases the entire hold. Reject amounts above
the authorization and settlements against unknown, declined, settled, or
cross-account authorizations. Honor an existing approval even if later ledger
movements reduced the balance; settlement validates the authorization rather
than making a second approval decision.

An identical settlement retry is a no-op. A different command cannot capture
the same authorization again, including after a partial settlement.

## 4. Caller order, value days, and historical views

**Gap.** The supplied commands are not in posted-day order: E10 has posted Day
5 but follows E9 on Day 6. The specification also mixes value-dated balances
with authorization state.

**Decision.** Process commands in supplied caller order and never sort them.
For a balance query, include every currently known entry whose value day is on
or before the requested day. Authorization approval uses posted day, while
ledger movements use value day.

`availableBalance` is a current-state projection, so it subtracts holds that
are approved now rather than reconstructing a historical hold snapshot. Daily
reports separately reconstruct authorization status from creation and status
days so the printed timeline remains understandable.

**Forward value dates are out of scope.** The specification supplies one
value-dated case and it is back-valued (E7). Nothing states what a value day
later than its posted day should mean, so accepting one would be inventing
policy — the same reasoning that rejects an invented BHD fee or an FX rate.
`LedgerCommandPayload` therefore rejects `valueDay > postedDay` as a
`LedgerArgumentException`: a malformed instruction rather than a business
decision about funds, so it is a caller error and never becomes a
`ProcessingError` in a replay.

The consequence is that every entry is visible to balances from the moment it
is processed, and the only temporal skew in the system is backwards. That keeps
one question open rather than two — "what did this day look like once we knew
everything" — and it is why balance projection needs no notion of a future
entry.

A production ledger would need the forward direction, because instructed
future-dated transfers are ordinary banking. It would arrive with its own
controls: a permitted forward window, warehousing of pending instructions until
value date, and cancellation before maturity. None of that is derivable from
the supplied stream.

## 5. Currency precision, fees, interest, and instalments

Several numerical policies are required but not fully specified:

- Use `HALF_EVEN` for calculated money. Reject input that would lose precision
  instead of silently rounding it.
- Do not charge an AED-denominated overdraft fee to a BHD account because no
  BHD fee or FX policy is supplied.
- Calculate daily interest from the positive closing balance after fees and
  before capitalization. Sum the individually rounded Day 1–6 accruals and
  append one capitalization per account at the end of Day 6. The capitalization
  itself earns no Day 6 interest.
- Split instalments in integer minor units and assign the residual to the
  earliest parts. BHD 10.000 over three parts is therefore 3.334, 3.333, and
  3.333 rather than three entries that exceed the source total. E10 asks for
  "three equal instalments" of an amount that cannot be divided equally at
  BHD precision, so the requirement is self-contradictory as supplied:
  equality and an exact 10.000 total are mutually exclusive here. Conservation
  of the source amount outranks equality of the parts, because an allocation
  that creates BHD 0.002 is unbookable in any ledger.

These policies keep every entry in its account currency and avoid invented FX
or self-referential interest.

## 6. Idempotency and rejection behavior

**Gap.** Duplicate delivery and invalid-command state changes are not defined.

**Decision.** Treat the supplied `eventId` as the command's idempotency key. An
identical successful retry is silent; reuse with different content raises
`CONFLICTING_EVENT_ID`. Authorization IDs are also unique, including declined
attempts. Validate before mutation so rejected commands consume no ID and move
no money.

Business rejections use `LedgerValidationException` with a stable code and are
captured as `ProcessingError` during replay. A balance query with a nonpositive
day uses `INVALID_BUSINESS_DAY`; construction errors such as blank identifiers,
invalid precision, or nonpositive command days use standard Java exceptions.
