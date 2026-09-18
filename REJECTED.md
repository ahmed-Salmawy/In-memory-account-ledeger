# Rejected Criteria and Approaches

## Conflicting criteria identified before implementation

- **Propagating E7 fees to later affected days:** rejected by the selected
  event-value-day policy. E7 assesses only Day 2; see AMBIGUITIES §1.
- **Auth-B remains active in the supplied replay:** E8 encounters at most
  AED -155.00 available before its requested AED 90.00 hold. Approval would
  violate the nonnegative-available rule. No expiry applies to approved
  holds, not declined requests. The implementation follows the approval rule;
  see AMBIGUITIES §2.
- **Three BHD 3.334 instalments:** total BHD 10.002 exceeds the source
  amount. The implemented 3.334 + 3.333 + 3.333 allocation preserves 10.000.

## Rejected implementation approaches

- Floating-point money and silent rounding of input transfers: both can
  alter money. Use exact BigDecimal input and explicit HALF_EVEN rounding
  for calculated values.
- Cached authoritative balances and mutable entries: derive balances from
  immutable opening data plus append-only signed entries.
- Sorting events by posted day: would move E10 before E9 and change the
  prescribed order.
- Separate balance service, future reference fields, and placeholder
  authorization/fee/interest classes: add them with their behavior.
- Frameworks, storage, and web layers: outside this assessment's scope.

The contradictory Auth-B-active interpretation is retained as an explicitly
disabled test. The runnable suite enforces the nonnegative-available rule.
