# Implementation Worklog

All timestamps are UTC. Entries record completed implementation and verification
milestones.

## 2026-09-18T06:09:51Z — Core ledger completed

- Added exact AED/BHD money handling with explicit `HALF_EVEN` calculated
  rounding and rejection of input precision loss.
- Added immutable accounts and append-only credit/debit entries with derived
  value-day balances.
- Added account, currency, amount, identifier, and idempotency validation.
- Verification: **31 tests, 0 failures, 0 errors**.

## 2026-09-18T06:31:40Z — Authorization holds completed

- Added immutable approved/declined authorization records and available-balance
  projection without booking holds into the ledger.
- Added duplicate-authorization protection, retry behavior, account isolation,
  and back-valued balance coverage.
- Verification: **45 tests, 0 failures, 0 errors**.

## 2026-09-18T06:35:14Z — Settlement completed

- Added settlement entries linked to approved authorizations.
- Released the complete hold after settlement, including unused authorized
  amount, and rejected unknown, declined, settled, cross-account, and
  over-capture requests before mutation.
- Verification: **57 tests, 0 failures, 0 errors**.

## 2026-09-18T06:59:26Z — Full replay completed

- Added append-only reversals and overdraft fee assessment/reversal on the
  processed command's value day.
- Added exact instalment allocation, daily interest, Day 6 capitalization,
  structured replay errors, daily reports, and the E1–E10 application.
- Verified deterministic replay, AED 466.03 and BHD 10.008 Day 6 balances,
  `UNKNOWN_AUTHORIZATION` for E6, and declined Auth-B.
- Verification: **63 tests, 0 failures, 0 errors, 1 documented skipped
  contradictory criterion**.

## 2026-09-18T13:35:02Z — Command and domain-event flow completed

- Split command processing into focused balance, authorization, transaction,
  fee, and interest classes while keeping flat `domain` and `service` packages.
- Renamed incoming work to `LedgerCommandPayload`/`CommandType` and published typed,
  past-tense domain events only after state changes.
- Moved fee reconciliation behind the common movement event and isolated
  listener failures without rolling back committed ledger state.
- Verification: **68 tests, 0 failures, 0 errors, 1 documented skipped
  contradictory criterion**.

## 2026-09-18T13:41:56Z — Decision documentation reviewed

- Reduced `AMBIGUITIES.md` to six material accounting decisions.
- Removed duplicated milestone narration and retained the chosen fee,
  authorization, settlement, projection, numeric, and idempotency policies.

## 2026-09-18T13:48:24Z — Acceptance criteria reviewed

- Mapped `REJECTED.md` directly to all eight supplied acceptance criteria.
- Corrected criterion 5 as a conditional statement, documented the append-only
  limitation in criterion 6, and recorded the monetary conflicts in criteria 7
  and 8.
- Replaced the invalid Auth-B alternative test with the documented contradictory
  BHD 3.334-per-instalment test.

## 2026-09-18T14:00:41Z — Submission verification

- Reviewed project structure, commands, documentation references, and source
  terminology against the final implementation.
- Ran `./mvnw clean verify`: **68 tests, 0 failures, 0 errors, 1 documented
  skipped contradictory criterion; BUILD SUCCESS**.

## 2026-09-18T14:03:30Z — Contradictory criterion activated

- Replaced the disabled criterion 7 example with an active, inline-labelled
  failing test as required by the assessment.
- Ran `./mvnw test`: **68 tests, 1 intentional failure, 0 errors, 0 skipped**.
  The failure compares the criterion's 3.334/3.334/3.334 allocation with the
  design's exact 3.334/3.333/3.333 allocation.

## 2026-09-18T17:27:01Z — Fee policy corrected and submission cleanup

- Re-reviewed criterion 2 against the non-negotiable daily rule: after E7 the
  pre-fee historical closings are negative on D2, D4, and D5, so a literal
  application assesses three fees, not one. Reclassified criterion 2 as
  rejected in `REJECTED.md` and updated criterion 6's fee arithmetic.
- Changed fee reconciliation to cover every day from a movement's value day
  through its posted day. E7 now assesses D2/D4/D5 fees; E9 appends all three
  `OVERDRAFT_FEE_REVERSAL` entries at the original fees' value days, keeping
  history append-only.
- Replaced the fee-eligibility balance with a pre-fee view that includes
  prior-day fees but excludes the target day's own fee or reversal, so a fee
  can never trigger itself.
- Final replay numbers are unchanged (AED 466.03, BHD 10.008, interest 1.03
  capitalized) because E9 reverses every fee before capitalization; Day 4 and
  Day 5 reports now list their fee/reversal pairs.
- Removed the confusing `./IMPLEMENTATION_PLAN.md` ignore rule and fixed the
  README run path: plain `./mvnw -o package` fails on the designed criterion 7
  test, so it now documents `./mvnw -o package -DskipTests`.
- Verification: `./mvnw test` **68 tests, 1 intentional failure, 0 errors**;
  `./mvnw -o package -DskipTests` builds and `ledger.LedgerApplication` runs.

## 2026-09-18T17:37:08Z — Explicit account currency implemented

- Changed `Account` to carry an explicit `Currency currency` record component
  instead of deriving it from the opening balance, with compact-constructor
  validation that the component matches `openingBalance.currency()`.
- Updated every account construction site in the application and test suites
  to pass the currency explicitly.
- Extended `validatesIdentifiersDaysAndEntrySigns` with a currency-mismatch
  rejection case.
- Updated `IMPLEMENTATION_PLAN.md`'s account model and README invariant 2 to
  describe explicit, validated currency configuration.
- Verification: `./mvnw test` **68 tests, 1 intentional failure, 0 errors**.

## 2026-09-18T17:46:43Z — Latest processed day bounds fee reconciliation

- Replaced the current command's posted day as the reconciliation upper bound
  with an explicit engine latest processed day: the maximum posted day processed
  so far, advanced by every non-duplicate command. Caller-ordered posted days
  need not be monotonic, so a later-arriving command posted D5 can change an
  already-known D6 closing balance; the latest processed day ensures such days are
  reevaluated.
- Added `reconciliationCoversDaysBeyondALaterArrivingCommandsPostedDay`
  (stale D6 fee reversed by a credit posted D5) and
  `latestProcessedDayReconciliationFeesADayNegativeMadeByALaterPostedCommand` (D6 becomes
  negative from a debit posted D5 and receives its fee).
- Updated the `OverdraftEventFeeProcessor` javadoc, AMBIGUITIES §1 decision, and
  README invariant 14.
- E1–E10 replay output unchanged (E9 already held the maximum posted day);
  verification: `./mvnw test` **70 tests, 1 intentional failure, 0 errors**.

## 2026-09-18T17:52:23Z — Rejected commands no longer move the latest processed day

- Moved the latest-processed-day update from the start of `processValid` to
  after the command is fully processed and committed, immediately before the
  processed event (and therefore the synchronous fee subscriber) is published.
  Previously a rejected command posted D100 advanced the latest processed day before
  account, currency, settlement, or reversal validation, so the next valid D1
  movement would have generated fees through D100.
- Added `rejectedCommandsDoNotExtendLatestProcessedDay`: a rejected
  currency-mismatch command posted D100 leaves a later valid D1 debit with
  exactly one Day 1 fee.
- E1–E10 replay output unchanged; verification: `./mvnw test`
  **71 tests, 1 intentional failure, 0 errors**.

## 2026-09-18T18:11:12Z — Latest-processed-day advancement assesses newly reached days

- Reconciliation no longer depends on ledger movement: after a successful
  command advances the latest processed day, every newly reached day is assessed
  for every account. Previously a D2 authorization following a negative D1
  debit advanced the latest processed day without creating the required Day 2 fee.
- Added `authorizationAdvancingLatestProcessedDayAssessesItsNewlyReachedDay` and split
  the declined-authorization test: a negative opening balance now accrues
  fees on days reached without movement (`negativeOpeningBalanceAccruesFeesOnDaysReachedWithoutMovement`);
  non-negative openings still produce no entries.
- Corrected `REJECTED.md` criterion 2's assessment table to the implemented
  eligibility view (prior-day fees included: D2 -370, D3 5, D4 -180,
  D5 -205; fee days unchanged) and `NUMBERS.md`'s fee line to name the
  latest processed day rather than the posted-day window.
- E1–E10 replay output unchanged; verification: `./mvnw test`
  **72 tests, 1 intentional failure, 0 errors**.

## 2026-09-18T18:15:36Z — Reconciliation bound renamed to latest processed day

- Renamed `assessmentHorizon` to `latestProcessedDay` across the engine,
  javadoc, and documentation; the name states the bound's purpose directly
  and is easier to explain.

## 2026-09-18T18:16:09Z — Replay assessment extends through the final report day

- Added `LedgerEngine.assessFeesThrough(day)` and called it from
  `LedgerReplay.replay` before interest capitalization. Previously the final
  successful command bounded assessment: commands ending on D4 with a report
  through D6 left negative accounts unassessed on D5/D6.
- Added `replayAssessesFeesThroughTheFinalReportedDay`. The supplied E1–E10
  replay is unaffected because E9 already reaches Day 6; output is unchanged.
- Verification: `./mvnw test` **73 tests, 1 intentional failure, 0 errors**.

## 2026-09-19T10:38:58Z — Class and method documentation added

- Added concise Javadoc to every main-source class (domain records and enums,
  all service processors, replay, reports, application) and to its critical
  methods: the commit sequence in `LedgerEngine.processValid`, the four-way
  per-day fee decision, the eligibility balance views, reversal compensation,
  hold approval/settlement rules, interest idempotency, and event delivery
  semantics. Comments state behavior and rationale, not restatements.
- No behavioral change; `./mvnw clean verify` compiles and packages, suite
  unchanged at **73 tests, 1 intentional failure, 0 errors**.

## 2026-09-19T10:41:14Z — JDK IllegalArgumentException replaced domain-wide

- Introduced `ledger.domain.exception.LedgerArgumentException` for caller errors in
  domain input (malformed identifiers, days, amounts, signs, duplicate
  account configuration, invalid report or interest periods) and replaced
  every `IllegalArgumentException` throw and test assertion with it.
  `LedgerValidationException` remains the distinct channel for business
  rejections with stable codes; `Objects.requireNonNull` null checks still
  raise NPE by convention.
- No behavioral change; suite unchanged at **73 tests, 1 intentional failure,
  0 errors**.

## 2026-09-18T18:22:32Z — Event subscription ownership separated

- Removed subscription registration and delivery-failure accessors from
  `LedgerEngine`; those operations belong to `InMemoryEventPublisher`.
- Added constructor injection for callers that observe events while retaining
  the one-argument engine constructor for ordinary use.
- Updated event-flow tests to subscribe and inspect failures through the
  publisher directly. Verification: `./mvnw test` **73 tests, 1 intentional
  failure, 0 errors**; offline packaging succeeds.

## 2026-09-18T18:25:05Z — Balance-day rejection receives a stable code

- Added `INVALID_BUSINESS_DAY` and changed balance and available-balance
  queries for nonpositive days to throw `LedgerValidationException`.
- Kept standard exceptions for invalid days while constructing commands and
  domain records.
- Verification: `./mvnw test` **73 tests, 1 intentional failure, 0 errors**;
  offline packaging succeeds.

## 2026-09-19T10:45:26Z — Production considerations documented

- Added `PRODUCTION_CONSIDERATIONS.md` covering append-only scaling, the UAE
  operational and regulatory impact of value dating, authorization lifecycle
  gaps, and production capabilities deliberately excluded from the assessment.
- Linked the document from the README.

## 2026-09-19T11:19:42Z — Cyclomatic complexity thresholds enforced

- Added one PMD rule only: methods at complexity 11–15 are review warnings;
  methods above 15 fail the build during `verify`.
- Reviewed the current warnings: balance projection and command processing are
  12; replay/report assembly is 11. No production method exceeds 15.
- Verification: `./mvnw -o -DskipTests pmd:check` succeeds with three review
  warnings; the priority-1 failure path was also exercised successfully.

## 2026-09-19T11:23:22Z — Command processing responsibilities extracted

- Extracted duplicate/currency validation from `processValid` into `validate`
  and command-type routing into `dispatch` without changing commit order.
- Verification: 44 relevant tests pass; PMD succeeds and `LedgerEngine` no
  longer appears in the 11–15 complexity review band.

## 2026-09-19T11:37:17Z — Identical retries remain no-ops

- Documented the checks performed by exception-only `validate`; identical
  retries throw a private exception caught at the public `process` boundary, stopping
  before `dispatch` without publishing a business rejection.
- Verification: all 14 `LedgerEngineTest` tests pass; PMD succeeds.

## 2026-09-19T12:29:59Z — Account lookup separated from balance API

- `LedgerEngine` now owns command account lookup and unknown-account rejection.
- `OverdraftEventFeeProcessor` receives the account it assesses; the calculator's
  lookup is private and used only to derive balance projections.
- Verification: full suite runs 73 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped. PMD succeeds.

## 2026-09-19T12:38:14Z — Command processing moved behind one boundary

- `LedgerEngine` now publishes validated commands through the `LedgerCommandProcessor`
  interface and has no concrete processor dependencies.
- External factory wiring creates the command dispatcher and the authorization,
  transaction, fee, and interest processors. The dispatcher records a command
  only after its selected processor succeeds.
- Verification: full suite runs 73 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped. PMD succeeds.

## 2026-09-19T12:43:09Z — Processor package wiring corrected

- Aligned the processor interface, factory, dispatcher, and publishers after
  the initial package move and updated engine imports.
- Verification: clean production compilation succeeds; the full suite has only
  the documented criterion 7 failure; PMD succeeds.

## 2026-09-19T12:50:19Z — Command and event packages separated

- Moved command types, routing, and command handlers under `service.command`.
- Moved processed events, publication, and the fee listener under
  `service.event`; replay errors remain with report output under `report`.
- Aligned the added enum and exception subpackages and updated documentation.
- Verification: clean production compilation succeeds; the full suite runs 73
  tests with only the documented criterion 7 failure; PMD succeeds.

## 2026-09-19T13:03:00Z — Command processing boundary narrowed

- `LedgerCommandProcessor` now contains only `process(LedgerCommandPayload)`.
- Fee reconciliation and interest remain independent event/day processors;
  neither operation passes through the command processor.
- Verification: clean production compilation and PMD succeed; the full suite
  runs 73 tests with only the documented criterion 7 failure.

## 2026-09-19T13:09:00Z — Engine initialization moved to factory

- `LedgerProcessorFactory` now creates the shared in-memory collections,
  balance calculator, and command, fee, and interest processors from the
  configured accounts.
- `LedgerEngine` receives the wired components and retains orchestration only.
- Verification: clean production compilation and PMD succeed; the full suite
  runs 73 tests with only the documented criterion 7 failure.

## 2026-09-19T13:25:54Z — Processor wiring split per side and scoped to one context

- Added `CommandProcessorInitializer` and `EventProcessorInitializer`, each
  wiring its own package's processors over state supplied by the caller.
- Added `LedgerContext`: one instance per engine owning the accounts, entries,
  processed commands, and authorizations, plus one instance of each processor
  built over them. Replaced the combined `LedgerProcessorFactory`.
- `LedgerEngine` now holds a single context handle instead of eight fields;
  account validation moved with the state it guards.
- Kept the context engine-scoped rather than static: processors close over the
  ledger collections, so a shared instance would break replay determinism.
- Verification: full suite runs 73 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped. PMD succeeds.

## 2026-09-19T13:31:42Z — Command boundary collapsed onto the dispatcher

- Deleted `InMemoryLedgerCommandProcessor`, a pass-through with no behavior, and
  the `LedgerCommandProcessor` interface it was the only implementation of.
- `CommandProcessorInitializer` now returns `LedgerCommandDispatcher` directly;
  the engine calls `dispatch` on it through the context.
- Verification: full suite runs 73 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped. PMD succeeds.

## 2026-09-19T13:44:10Z — Engine stopped subscribing to its own events

- Removed the constructor subscription that fed completed movements back into
  fee reconciliation; the engine had been its own only production subscriber.
- Added one private `reconcileFees` path used both after a movement and for each
  newly reached day, replacing the previous split listener/direct-call routes.
- `InMemoryEventPublisher` is now an output port only: every publication site is
  in the engine and no production code subscribes, so no listener publishes
  while another event is being delivered.
- Event content and ordering are unchanged; `LedgerEventFlowTest` passes
  untouched.
- Verification: full suite runs 73 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped. PMD succeeds.

## 2026-09-19T13:58:27Z — Command routing moved to declared handler types

- Added `LedgerCommandHandler` with one implementation per command type —
  credit, debit, reversal, authorization, and settlement — each declaring the
  type it serves through `handles()`.
- `LedgerCommandDispatcher` now resolves handlers from an `EnumMap` registry
  instead of switching on the command type; a new command type is a new handler
  rather than an edited switch.
- The registry cannot fail to compile the way an exhaustive enum switch does, so
  construction rejects duplicate and missing handlers. Every engine builds a
  dispatcher, so a gap surfaces on first construction.
- Added `LedgerCommandDispatcherTest` covering both guard paths.
- Verification: full suite runs 75 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped. PMD succeeds.

## 2026-09-19T14:09:33Z — Authorization processing merged into its handlers

- Removed `AuthorizationProcessor`. Its two public methods had exactly one
  caller each, and `SettlementResult` existed only to carry two values across
  that boundary into the event the caller built immediately after.
- `AuthorizationHandler` now owns the duplicate check and the approve/decline
  decision; `SettlementHandler` owns the hold validations, the SETTLED
  transition, and the debit booking, constructing its event directly.
- `TransactionProcessor` stays: it is the shared entry writer behind four call
  paths, owning the entries list, entry-ID minting, and reverse-once state.
- Validation order and error codes are unchanged; `AuthorizationTest` and
  `SettlementTest` pass untouched.
- Verification: full suite runs 75 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped. PMD succeeds.

## 2026-09-19T14:31:05Z — Packages named for what triggers them; wiring folded back

- Renamed `OverdraftEventFeeProcessor` to `OverdraftFeeProcessor` and moved it
  and `InterestProcessor` from `service/event/processor` to `service/daily`.
  Both take business days rather than events and never saw a
  `LedgerDomainEvent`; the package name had been asserting a mechanism they do
  not use. `service/event` now holds only the event model and its publisher.
- Removed `CommandProcessorInitializer` and `EventProcessorInitializer`.
  `LedgerContext` was the only caller of either and now constructs the handlers,
  dispatcher, fee, and interest processors directly — one wiring class again,
  with the same per-engine scoping.
- Verification: full suite runs 75 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped. PMD succeeds.

## 2026-09-19T14:41:52Z — Implementation plan component list reconciled

- Rewrote `IMPLEMENTATION_PLAN.md` §10 against the built tree: the command
  subpackages, the handler set, `service/daily`, `LedgerContext`, and the real
  test classes replace the superseded processor and factory names.
- Recorded the organizing rule the packages now follow — split by what triggers
  the work rather than by layer.
- `DESIGN.md` needed no change: it names no classes or packages, and its
  concepts and idempotency examples still match the implementation.
- Verification: full suite runs 75 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped. PMD succeeds.

## 2026-09-19T15:04:18Z — Domain event machinery removed

- Deleted `service/event` entirely: `LedgerDomainEvent`'s eight records and
  `InMemoryEventPublisher`. Nothing in production had subscribed since the
  engine stopped listening to itself, so every publication was delivered to an
  empty subscriber list.
- `LedgerCommandHandler.handle` now returns the entries it booked and
  `LedgerCommandDispatcher.dispatch` passes them through. The fee gate is
  `if (!booked.isEmpty())` — an authorization books nothing, so a hold still
  never triggers a fee, previously expressed as `instanceof MovementProcessed`.
- `LedgerEngine` no longer takes or holds a publisher; business rejections
  propagate rather than being published and rethrown.
- Removed `LedgerEventFlowTest`, which covered only the deleted mechanism.
- `DESIGN.md` §Scope Discipline excludes messaging machinery by name; a
  publisher with no subscriber belonged in that list.
- E1–E10 output is unchanged: Day 6 AED 466.03 and BHD 10.008.
- Verification: full suite runs 70 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped. PMD succeeds.

## 2026-09-19T15:29:40Z — Booking logic moved into the handlers that own it

- Replaced `TransactionProcessor` with `LedgerEntryBook`. Two of its three
  fields — `processedCommands` and `reversedEventIds` — were read only by
  `reverse()`, so the class was an entry writer with reversal logic attached.
- `LedgerEntryBook` keeps what is genuinely shared: the entries list, the
  `post` factory, and the entry-ID convention every booking path depends on.
  It also answers `bySourceEvent` for reversal lookups.
- `CreditHandler` now owns instalment allocation, `DebitHandler` and
  `SettlementHandler` post their own entries, and `ReversalHandler` owns the
  reversal validations, the reversible-type filter, and the reverse-once set.
  None of the five handlers delegates without adding anything.
- E1–E10 output is unchanged: Day 6 AED 466.03 and BHD 10.008.
- Verification: full suite runs 70 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped. PMD succeeds.

## 2026-09-19T15:52:10Z — Documentation reconciled with the restructured packages

- `LedgerEntryBook` is listed under `domain` in the README structure block, the
  implementation plan component tree, and CLAUDE.md.
- Removed the reference to retained event-delivery failures from
  `PRODUCTION_CONSIDERATIONS.md`; the publisher that produced them is gone.
- Verification: full suite runs 70 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped. PMD succeeds. E1–E10 Day 6 balances remain
  AED 466.03 and BHD 10.008.

## 2026-09-19T16:18:05Z — PMD complexity gate removed

- Dropped the `maven-pmd-plugin` configuration and `pmd-ruleset.xml`. Earlier
  entries record that PMD passed at the time they were written; the gate is no
  longer part of the build, so later verification lines cite tests only.
- Verification: full suite runs 70 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped.

## 2026-09-19T17:06:41Z — Calendar-driven processors returned to service/daily

- Moved `OverdraftFeeProcessor` and `InterestProcessor` back under
  `service/daily`. Both hold their own policy state — active fees and fee
  cycles, the daily rate and the accounts they capitalize for — so they stay
  separate from the engine, which owns orchestration: the reconciliation span,
  day advancement, and command validation.
- Service packages are named for what triggers the work: `command` for an
  arriving instruction, `daily` for time advancing.
- Structure blocks in README, CLAUDE.md, and the implementation plan updated
  for the current layout, including the command payload, `CommandType`, and
  `LedgerEntryBook` now living under `domain`.
- Verification: full suite runs 70 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped.

## 2026-09-19T17:41:22Z — Forward value dating removed; entry book returned to the service layer

- `LedgerCommandPayload` now rejects `valueDay > postedDay`. The specification
  supplies only a back-valued case, so accepting the forward direction was
  inventing policy. It is a `LedgerArgumentException`: malformed input rather
  than a funds decision, so a replay never records it as a `ProcessingError`.
  `approvalUsesPostedDayAndAllKnownHoldsInCallerOrder` keeps every assertion
  using a same-day credit whose value day still falls after the hold request.
- Moved `LedgerEntryBook` from `domain` to `service.command`. It wraps and
  mutates the shared entry list, so it is a service; `domain` is immutable
  values only and again has no outbound imports.
- Re-argued REJECTED criterion 6 on the criterion's own terms: an assessed and
  reversed fee is not the same state as no fee, which is visible in the Day 2
  report. The Auth-B point now supports the argument rather than carrying it.
- Verification: full suite runs 70 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped.

## 2026-09-19T18:22:40Z — Comment artifacts cleared; authorization section split by what exists

- Removed six rename artifacts left in prose: `AccountBalanceCalculator` had
  "opening balanceCalculator", "feesProcessor and interestProcessor", and
  "prior-day feesProcessor count"; `LedgerReplay` had "processes
  commandsProcessor" twice and "capitalizes interestProcessor". A scan of every
  comment line in main and test sources now finds no identifier used as prose.
- `PRODUCTION_CONSIDERATIONS.md` authorization section now separates the two
  terminal outcomes the implementation actually has — declined at creation, and
  settled below the authorized amount — from the five that production requires
  and this model lacks.
- Verification: full suite runs 70 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped.

## 2026-09-19T18:58:12Z — Fee walkthrough checked against the engine and documented

- Replayed E1–E10 through the page's own JavaScript model and diffed it against
  `LedgerApplication`. The fifteen command and fee entries match exactly in
  booking order, entry ID, value day, and sign, as does the
  `UNKNOWN_AUTHORIZATION` rejection for E6. Interest is out of the page's scope
  and is the only difference.
- One divergence found and fixed: the sandbox accepted a value day later than
  its posted day, which the engine now rejects. The model rejects it as
  `INVALID_VALUE_DAY`, the scope notes state the rule, and the input carries the
  constraint.
- README describes the page, its narrower scope, and that the engine and test
  suite remain the source of truth if the two disagree.
- Verification: full suite runs 70 tests with only the documented criterion 7
  failure; 0 errors and 0 skipped.
