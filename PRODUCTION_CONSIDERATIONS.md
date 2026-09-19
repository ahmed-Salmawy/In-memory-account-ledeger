# Production Considerations

## Append-only at scale

At 100× volume, CPU fails before append-only storage. Every balance view is a
full scan of the shared entry list, and nothing caches a result. Fee
reconciliation performs one such scan per day in its span, and the engine
reconciles every account on every newly reached day, so each additional entry
raises the cost of every later projection.

Replay is therefore quadratic in entry count, not linear:

```text
balance(account, day)          O(n)          scan of all entries
reconcile(account, span)       O(days × n)   one projection per day
replay of m commands           O(m² × days × accounts)
```

100× the volume is roughly 10,000× the work. Storage is unaffected — entries
are small and append-only is the cheapest possible write pattern — so the
failure is latency, and it arrives suddenly rather than gradually.

State is also unbounded. Ledger entries, processed command IDs, authorizations,
reversed-event IDs, active-fee state, and fee-cycle counters remain in heap for
the engine's lifetime. Reports additionally materialize
every requested account/day row.

The cheapest structural improvement is an account/value-day index, for example a
map from account ID to a value-day-ordered entry collection. It preserves the
current model while limiting projections to one account and the relevant dates.
Periodic immutable balance checkpoints can later bound replay length. Production
would ultimately place the journal and idempotency keys in durable storage, retain
only active operational state in memory, and rebuild projections from checkpoints
plus subsequent entries.

## Value-dated entries in production

A value date changes more than a displayed balance. A back-valued posting can
alter closed-day interest, fees, available funds, customer statements, general
ledger reconciliation, financial and regulatory reports, complaint evidence, and
the chronology seen by transaction-monitoring systems. Corrections must propagate
to every affected projection without erasing what was originally booked.

For a UAE-licensed bank, that creates an audit, conduct, data, and financial-crime
surface. CBUAE Consumer Protection Standards require detailed statements and
transparent disclosure of transactions, interest, and fees, while consumer and
transaction records must be securely retained and stored in the UAE. CBUAE AML
guidance requires transaction records sufficient to reconstruct activity and
support monitoring; the current UAE AML executive regulation requires relevant
transaction records to be retained for at least five years. See the
[CBUAE Consumer Protection Standards](https://rulebook.centralbank.ae/en/rulebook/consumer-protection-standards),
[CBUAE transaction-monitoring guidance](https://rulebook.centralbank.ae/en/rulebook/36-transaction-monitoring-and-suspicious-transaction-reporting),
and [UAE AML Executive Regulation](https://uaelegislation.gov.ae/en/legislations/3857/download).

Before go-live I would add one control: maker-checker approval for every manual or
exceptional back-valued posting. The immutable approval record would contain the
original posting timestamp, requested value date, reason, evidence, initiator,
approver, and an impact preview covering balances, fees, interest, statements,
accounting, and financial-crime monitoring. The system would reject entries
outside the permitted backdating window and prove that every affected downstream
projection was recalculated.

## Authorization lifecycle

### Terminal outcomes in the implemented model

Two, and neither is a matching settlement.

| Outcome | Real-world scenario | Behavior |
| --- | --- | --- |
| Declined at creation | Available funds did not cover the requested hold at the moment of the request. | Retain the attempt for audit and reserve nothing. The decision is evaluated once and never re-run, so a later credit or reversal that restores the balance does not revive it, and the same authorization ID can never become approved. |
| Settled below the authorized amount | A merchant captures less than it authorized — a lower final amount than the estimate, a partial shipment, an absent tip. | Book the captured debit, mark the authorization SETTLED, and release the **entire** hold, including the uncaptured remainder. The difference returns to available funds immediately, because the model has no residual hold and no concept of a further capture. |

An approved hold has no other exit, so it can reserve funds indefinitely. A
failed or unknown settlement does not end an authorization, and a duplicate
authorization ID is rejected rather than replacing the existing hold.

### Required in production, not implemented

| Outcome | Real-world scenario | Mandated behavior |
| --- | --- | --- |
| Expired | The merchant never submits a capture before the scheme/product deadline. | Release the remaining hold exactly once, retain the original authorization, and record expiry time and policy. |
| Voided/cancelled | The merchant or customer cancels before capture. | Authenticate the request, append the lifecycle change, release the remaining hold, and make retries idempotent. |
| Reversed by network | A late authorization response, timeout recovery, or scheme reversal invalidates the hold. | Correlate by network reference, release only the matching hold, and retain both messages for reconciliation. |
| Revoked by issuer | Fraud controls, sanctions action, account closure, or an administrative block requires withdrawal. | Require an authorized reason code, release or freeze funds according to policy, and notify monitoring and customer-service systems. |
| Partially captured then closed | A merchant captures less than authorized and confirms no further captures. | Book each permitted capture, track the remaining amount, then release only the residual hold. |

These outcomes need explicit states or immutable lifecycle events; silently deleting
an authorization would destroy the evidence needed for disputes and reconciliation.

## What was cut and why

| Simplification | Why it stayed out | Production risk deferred |
| --- | --- | --- |
| In-memory persistence | Keeps the assessment focused on ledger behavior rather than infrastructure. | A restart loses entries, idempotency keys, and holds; production needs durable storage, recovery, audit retention, and reconciliation. |
| Full-history balance scans | The small replay makes direct calculation simple and easy to verify. | Work grows with account history; snapshots or indexed running totals become necessary at scale. |
| Single-process execution, no clock | Caller order gives deterministic replay without concurrency machinery, and days advance only when a command or report asks for them. | Production needs per-account ordering, concurrency control, recovery from partial failure, and a scheduled end-of-day assessment. Fee assessment is already decoupled from movement — every account is assessed on each newly reached day — so what is missing is the trigger, not the logic. |
| No event publication | An in-process publisher was built and removed once it was clear nothing consumed it. | Real consumers are cross-process, so publishing becomes a second write: commit then publish risks a lost event, publish then commit risks one for a rolled-back transaction. A transactional outbox is the usual answer, though an append-only journal is already the log an outbox would duplicate, so change data capture over the entries table gives the same at-least-once guarantee without a second table. Consumers must be idempotent, and partitioning by account preserves the caller ordering fee reconciliation depends on. |
| Simplified authorization lifecycle | The scenario requires approval, decline, and a single capture that closes the hold. | Expiry, cancellation, network reversal, partial capture, and multiple capture need explicit idempotent lifecycle transitions. |
| Uncapped fee assessment | The rule states a per-day test and no limit; a cap would override it. | A fee is itself a booked entry, so it counts toward later closing balances and can drive an account negative into a further fee. Production needs a per-period cap, or a rule that fee-induced overdrafts are not fee-bearing. See AMBIGUITIES §1. |
| Simplified historical adjustments | Integer days and final value-date projections are enough for the supplied back-valued event; forward value dating is rejected. | Production needs cut-off calendars, approval controls, statement versions, customer notifications, and an audit trail of what was known before each adjustment. |

These categories group the deliberate scope cuts that materially affect this
ledger rather than listing every feature of a production banking platform.
