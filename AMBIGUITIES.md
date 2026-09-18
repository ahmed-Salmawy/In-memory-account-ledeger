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

**Decision.** Reconcile only the processed financial command's value day. Test
fee eligibility against the pre-fee balance so a fee cannot trigger another
fee. If a later command makes that day non-negative, preserve the original fee
and append an equal `OVERDRAFT_FEE_REVERSAL`.

A reversal compensates only the referenced command's booked movement. Fee
reconciliation then independently decides whether its derived fee remains
valid. Consequently, E7 creates one Day 2 fee and E9 causes its append-only
reversal.

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
  3.333 rather than three entries that exceed the source total.

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
captured as `ProcessingError` during replay. Basic construction errors such as
blank identifiers, invalid precision, or nonpositive days use standard Java
exceptions.
