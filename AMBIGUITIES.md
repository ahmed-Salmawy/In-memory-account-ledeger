# AMBIGUITIES

This document records places where the specification permits more than
one reasonable interpretation. In a production implementation these
would be confirmed with product, finance, and accounting stakeholders.
For this assessment, each ambiguity receives a deterministic documented
decision so implementation can proceed.

## 1. Back-Valued Transaction and Subsequent Daily Fees

### Ambiguity

E7 arrives on Day 5 with `value_date = Day 2`.

Once inserted into the value-date projection, its financial effect may
cause not only Day 2 but subsequent daily closing balances to become
negative until later entries offset it.

The specification says:

> overdraft fee is assessed once per day per account when that day's
> closing ledger balance is negative

but an acceptance criterion says:

> E7 causes exactly one overdraft fee to be assessed, on Day 2.

Those statements can conflict depending on whether back-valued effects
propagate through every subsequent daily close.

### Chosen interpretation

Treat this as an explicit acceptance-criteria conflict rather than
silently generalizing the fee to later days. Implement the chosen rule
consistently and document the conflicting criterion in `REJECTED.md` if
mathematical replay shows it cannot satisfy the core rule.

### Production resolution

Confirm whether overdraft assessment is:

1.  recalculated for every affected historical closing day, or
2.  assessed only against the event's value day when a late event
    arrives.

------------------------------------------------------------------------

## 2. Can a Back-Valued Correction Invalidate an Existing Fee?

### Ambiguity

Suppose a day was negative and received an overdraft fee. A later
back-valued credit/reversal could make that historical day non-negative.

The specification prohibits mutation/deletion but does not explicitly
define fee correction behavior.

### Chosen interpretation

Never remove the original fee. If the recalculated rule says it is no
longer justified, append an equal and opposite `OVERDRAFT_FEE_REVERSAL`
entry linked to the original fee.

This preserves append-only audit history.

------------------------------------------------------------------------

## 3. Does E9 Reverse Only E7 or Also Consequential Fees?

### Ambiguity

E9 says it "reverses E7." It does not explicitly say that fees caused by
E7 are automatically reversed.

### Chosen interpretation

E9 directly compensates only E7's principal ledger movement.

Fees are independently reconciled by the fee engine against the
recalculated daily closing state. If a fee is no longer valid under the
selected fee policy, the fee engine appends a fee reversal.

This avoids making a reversal implicitly own unrelated derived entries.

------------------------------------------------------------------------

## 4. Fee Included in the Same Day's Closing Balance

### Ambiguity

The fee is triggered by a negative closing balance and is itself booked
with the same value date. This creates a question of whether "closing
balance" means before or after fee assessment.

### Chosen interpretation

Use the **pre-fee closing balance** to determine fee eligibility. Once
assessed, the fee becomes part of the final ledger balance for that
value day.

Do not recursively assess another fee because the first fee made the
balance more negative.

------------------------------------------------------------------------

## 5. Interest Balance: Before or After Overdraft Fees?

### Ambiguity

The specification says interest is calculated on "closing ledger
balance" but does not explicitly state the ordering between fee
assessment and interest accrual.

### Chosen interpretation

Assess applicable overdraft fees first, then calculate interest from the
final closing ledger balance for the day.

Since interest applies only to positive balances, this ordering is
deterministic and conservative.

------------------------------------------------------------------------

## 6. Does Day 6 Capitalized Interest Earn Day 6 Interest?

### Ambiguity

Interest accrues daily and "capitalizes as a single credit at end of Day
6."

If the capitalization credit were included in the Day 6 interest base,
the calculation would become self-referential.

### Chosen interpretation

Calculate Day 6 daily interest from the Day 6 closing ledger balance
**before** the capitalization entry. Then sum rounded D1--D6 accruals
and append one capitalization credit at end of Day 6.

The capitalization itself earns no additional Day 6 interest.

------------------------------------------------------------------------

## 7. Daily Interest Rounding Mode

### Ambiguity

Currency precision is specified, but the rounding mode is not.

### Chosen interpretation

Use `RoundingMode.HALF_EVEN` for financial rounding.

The selected rounding mode is centralized and documented rather than
relying on a library default.

------------------------------------------------------------------------

## 8. Three "Equal" BHD Instalments

### Ambiguity

BHD 10.000 cannot be represented as three numerically identical amounts
at 3 decimal precision.

### Chosen interpretation

Use deterministic residual allocation:

``` text
3.334
3.333
3.333
```

The first instalment absorbs the one-mill residual.

"Equal" is interpreted as equal as currency precision permits while
preserving the exact original total.

------------------------------------------------------------------------

## 9. Settlement Smaller Than Authorization

### Ambiguity

Auth-A holds AED 200 but settles for AED 185.

The requirements do not explicitly state what happens to the remaining
AED 15 hold.

### Chosen interpretation

Settlement completes Auth-A and releases the entire AED 200 hold. Only
AED 185 is booked to the ledger.

No residual AED 15 hold remains.

------------------------------------------------------------------------

## 10. Settlement Greater Than Authorized Amount

### Ambiguity

The supplied stream does not contain an over-capture case and the
requirements do not define whether settlement may exceed the authorized
amount.

### Chosen interpretation

Do not invent support for over-capture. Reject settlement amounts
greater than the approved authorization amount.

This rule should be confirmed with the business in production.

------------------------------------------------------------------------

## 11. Authorization Expiration / TTL

### Ambiguity

Auth-B remains unsettled inside the six-day window. No authorization
expiration period is provided.

### Chosen interpretation

Do not invent a TTL.

Auth-B remains active through the end of Day 6 unless explicitly
settled/reversed by an event.

------------------------------------------------------------------------

## 12. Declined Authorization Representation

### Ambiguity

The output requires authorization states, but the specification does not
say whether a declined authorization should remain represented.

### Chosen interpretation

Retain an authorization record with status `DECLINED` for
audit/reporting, but create no active hold.

------------------------------------------------------------------------

## 13. Event Idempotency

### Ambiguity

The supplied stream has unique IDs, but duplicate delivery semantics are
not explicitly described.

### Chosen interpretation

Treat `eventId` as unique. Processing the same event ID twice must not
duplicate financial effects.

This supports deterministic replay and prevents duplicate
settlement/reversal/fee generation.

------------------------------------------------------------------------

## 14. Date Representation

### Ambiguity

The domain uses abstract Day 1--Day 6 rather than real timestamps.

### Chosen interpretation

Represent the assessment window using a small `BusinessDay`/integer-like
value rather than inventing real calendar dates or time zones.

If real dates are used for implementation convenience, ordering remains
based only on the six supplied business days.

------------------------------------------------------------------------

## 15. Account Currency Mismatch

### Ambiguity

The requirements establish one currency per account but do not specify
behavior for an event in another currency.

### Chosen interpretation

Reject any event whose currency differs from the account currency. No FX
conversion is in scope.

------------------------------------------------------------------------

## 16. What Exactly Is Printed "Per Day"?

### Ambiguity

The acceptance text explicitly requires closing ledger balance, fee
assessments, authorization states, and errors. Available balance and
daily interest are important derived values but are not explicitly named
in that output sentence.

### Chosen interpretation

Print them as well because they make authorization and interest behavior
inspectable:

``` text
closing ledger balance
available balance
fees
authorization states
daily interest
errors
```

This adds observability without changing financial behavior.

------------------------------------------------------------------------

## 17. First-Milestone Findings: Fee Count and Auth-B

Before fees, E7 changes ACC-001's historical balances to AED -370.00 on
Day 2, 30.00 on Day 3, and -155.00 on Days 4 and 5. Therefore the daily
negative-balance rule cannot produce only one Day 2 fee. Section 1 flags
this conflict but does not actually choose a historical fee policy.

At E8, available funds are at most AED -155.00: Auth-A has settled and
fees can only reduce that balance. Auth-B's AED 90.00 authorization must
be declined under the approval rule. The statement that Auth-B remains
active conflicts with that rule. No expiry remains the policy for
approved holds; it cannot turn a declined authorization into an active one.
E9 arriving later does not explicitly request another authorization attempt.

These conflicts are recorded for review before the authorization/fee
milestones. Neither behavior is implemented in milestone 1.

## 18. Opening Balance and Money Representation

The suggested Account fields omit opening balance, but the balance formula
requires it. Store an immutable opening `Money` value on Account; derive
the account currency from that value. This is configuration, not a running
balance. Event and entry amounts also carry their currency through Money,
avoiding a second currency field that could disagree.

## 19. Input Precision Versus Calculated Rounding

Rounding of externally supplied amounts is unspecified. Milestone 1 rejects
amounts with nonzero digits beyond currency precision using `UNNECESSARY`;
it accepts extra trailing zeros and normalizes scale. This prevents silently
changing an input transfer. Calculated amounts can explicitly use
`Money.rounded`, which applies the established `HALF_EVEN` policy. CREDIT
and DEBIT input amounts must be strictly positive; entry type determines
the booked sign. Zero or negative input transfers are rejected.

## 20. Duplicate IDs and Invalid Milestone Inputs

An identical successfully posted event is a no-op on retry. Reusing its ID
with different content is rejected rather than silently dropping a different
transfer. Invalid input must leave entries and deduplication state unchanged.
Milestone 1 exposes validation exceptions to its Java caller; structured
ProcessingError collection belongs to the later replay/reporting milestone.

## 21. Event Order, Day Queries, and Incremental Models

E10 has postedDay 5 but follows E9 with postedDay 6. Caller-supplied order
is authoritative; never sort or require monotonically increasing postedDay.
Use positive integer business days. A balance query uses all entries
currently known with valueDay <= the query day; it is not a posted-day
snapshot. Historical hold/report snapshots still need an explicit reporting
decision in their milestone.

Milestone 1 models only CREDIT and DEBIT. Authorization/reversal reference
fields and additional enum values will arrive with their actual behavior.
Balance projection stays in LedgerEngine until another component needs it.

## 22. Cross-Currency Fees and Capitalization Scope

The fee is denominated in AED, but ACC-002 uses BHD and FX is excluded.
The BHD overdraft policy is unspecified. Also, one capitalization credit
cannot combine AED and BHD; clarify whether “ONE” means one per account.
These do not affect the first milestone and remain open for the fee/interest
review. No conversion rate or multi-currency posting is invented.
