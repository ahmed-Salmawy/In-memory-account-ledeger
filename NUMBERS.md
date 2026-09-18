# Numeric Decisions

## Implemented domain constants

| Value | Reason |
| --- | --- |
| AED scale `2` | The required smallest representable AED amount is 0.01. |
| BHD scale `3` | The required smallest representable BHD amount is 0.001. |
| `HALF_EVEN` | The rounding policy chosen in AMBIGUITIES §5; exact ties choose the even last digit. |
| `UNNECESSARY` on input | Reject precision loss rather than silently changing an instructed transfer; extra trailing zeros are exact. |
| Amount sign `> 0` | Input transfers are magnitudes; event type determines the posting direction. |
| CREDIT/fee reversal/interest sign `1`; DEBIT/SETTLEMENT/fee sign `-1` | Signed booked amounts implement addition/subtraction; REVERSAL uses the opposite source sign. |
| First valid day `1` | The assessment uses one-based abstract business days. No maximum is invented in the reusable records. |
| Opening AED `0.00`, BHD `0.000` | Supplied account configuration, not defaults hardcoded into the engine. |
| Available after hold `>= 0` | Approve an authorization only when current available funds cover the full amount; zero remaining is valid. |

## Assessment fixture values

| Value | Source and intended meaning |
| --- | --- |
| Days `1` through `6` | Supplied replay/reporting window. |
| E1 `1200.00`, E2 `950.00` AED | Supplied credit and debit; milestone tests derive a net 250.00. |
| E3 `200.00`, E4 `400.00`, E5 `185.00`, E6 `180.00` AED | Supplied hold, credit, known settlement, and unknown settlement; no substitutions. |
| E7 `620.00`, E8 `90.00` AED | Supplied back-valued debit and attempted hold. E9 references E7's amount rather than inventing a new one. |
| Fee `25.00` AED | Applied once to the processed event's negative value day; BHD receives no AED fee without FX. |
| Daily rate `0.0004` | Exactly 0.04 / 100, with no annualization or compounding invented. |
| Capitalization day `6` | Supplied end of window; capitalization must sum individually rounded accruals. |
| E10 `10.000` BHD, `3` instalments | Supplied total/count; base 3.333 leaves 0.001 allocated to the first instalment: 3.334, 3.333, 3.333. |

These values are implemented by the E1–E10 scenario. Other small amounts in unit tests are synthetic fixtures: 0.10 +
0.20 checks decimal arithmetic; 1.225/1.235 and 1.2345/1.2355 check ties in
both currency scales; 50 + 20 - 10 checks nonzero opening balance and day
filtering. They introduce no business rules.

## Toolchain versions

- Java release `17`: supports records and switch expressions and matches an
  installed JDK; no newer language features are needed.
- Maven `3.9.11`: pins the installed/cached distribution through the wrapper.
- Wrapper `0.5.5`: standard bootstrap available locally; reused offline,
  not a claim that it is the newest wrapper release.
- JUnit Jupiter `5.10.2`: cached JUnit 5 API/engine with parameterized tests;
  test scope only.
- Compiler plugin `3.13.0`, Surefire `3.2.5`: pinned cached versions that
  compile with release 17 and discover JUnit Jupiter tests.
- Project `1.0-SNAPSHOT`: unpublished development version, not a release.
