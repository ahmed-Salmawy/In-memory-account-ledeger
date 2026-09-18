# Rejected Criteria and Approaches

## Conflicting criteria identified before implementation

- **Exactly one Day 2 overdraft fee after E7:** principal-only balances are
  AED -370.00 on D2, 30.00 on D3, and -155.00 on D4–D5. The daily
  negative-closing rule also reaches D4 and D5. Historical reconciliation
  must be resolved before fees are implemented; see AMBIGUITIES §1 and §17.
- **Auth-B remains active in the supplied replay:** E8 encounters at most
  AED -155.00 available before its requested AED 90.00 hold. Approval would
  violate the nonnegative-available rule. No expiry applies to approved
  holds, not declined requests. This conflict awaits milestone review.
- **Three BHD 3.334 instalments:** total BHD 10.002 exceeds the source
  amount. The documented 3.334 + 3.333 + 3.333 allocation preserves 10.000;
  allocation implementation is deferred.

## Implementation approaches rejected in milestone 1

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

The implementation plan's deliberately failing interpretation test belongs
to the later assessment milestone, when its conflicting policy is selected.
It is not silently removed or added to this milestone's passing test suite.
