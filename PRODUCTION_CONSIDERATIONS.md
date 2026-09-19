# Production Considerations

## Append-only at scale

At 100× volume, CPU fails before append-only storage. Balance, available-balance,
fee, interest, and report projections repeatedly scan the shared entry list;
fee assessment adds account-by-day loops around those scans. Replay cost therefore
grows much faster than the number of entries, and latency becomes unpredictable.

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

In the implemented model, the only non-settlement terminal outcome is
`DECLINED`. It represents insufficient available funds at authorization time. The
attempt remains recorded, creates no hold, and cannot later become approved under
the same authorization ID. A failed or unknown settlement does not end an
authorization. An approved authorization has no other exit and can therefore hold
funds forever.

Production needs additional terminal outcomes:

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
| In-memory state only | Keeps the assessment focused on ledger behavior. | Restart loses money state, idempotency keys, holds, and audit evidence; there is no recovery or high availability. |
| Linear projections | Small data makes the simplest calculation verifiable. | Latency and CPU grow with history and report size. |
| Single-threaded caller order | Avoids locking and makes replay deterministic. | No concurrent account processing, partition ordering, or race protection. |
| In-process synchronous events | Demonstrates decoupling without infrastructure. | No durable delivery, retry, outbox, dead-letter handling, or cross-service recovery. |
| Minimal account record | Only identity, currency, and opening balance are needed here. | No ownership, lifecycle status, blocks, product terms, limits, or legal restrictions. |
| Minimal authorization states | The supplied scenario needs approval, decline, and settlement only. | Holds can live forever; expiry, void, reversal, incremental authorization, multiple capture, and dispute flows are absent. |
| One settlement closes the hold | Avoids inventing capture policy. | Real partial and multiple-capture behavior cannot be represented. |
| Fixed currency scales, fee, and interest policy | Avoids a speculative product-configuration layer. | Product versioning, rate changes, tiering, calendars, tax, Shari'ah treatment, and effective-dated pricing are absent. |
| No FX conversion | No exchange-rate source or FX rule was supplied. | Cross-currency postings, spreads, rate timestamps, and FX gain/loss accounting are unsupported. |
| Abstract integer days | Sufficient for deterministic examples. | No UAE timezone, holidays, cut-offs, leap days, day-count conventions, or future-value-date policy. |
| Final value-date reports | Avoids a second temporal model. | The system cannot answer “what was known at that time” without a processing-time dimension and statement versions. |
| Local validation and exception capture | Enough to demonstrate deterministic rejection. | No API authentication, authorization, maker-checker workflow, case management, or operational alerting. |
| No core-GL, payment-network, or settlement reconciliation | External systems are outside scope. | Breaks, duplicates, missing messages, nostro differences, and end-of-day imbalance may go undetected. |
| No production data controls | There is no database or customer deployment. | Retention, UAE residency, encryption, key management, access logging, legal hold, archival, and deletion controls remain unimplemented. |

These cuts keep the implementation small enough to reason about. They are boundaries,
not claims that the same process is ready to hold customer money.
