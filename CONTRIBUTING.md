# Contributing

`cloud-itonami-emi` accepts contributions to the OSS actor, governor
tests, documentation, jurisdiction packs and open business blueprint.

## Development

```bash
clojure -M:dev:test
clojure -M:lint
```

Keep changes small and include tests for governor, phase, registry or
facts-coverage behavior.

## Rules

- Do not commit real customer applications, credentials, identification
  documents, wallet balances or screening results.
- Keep e-money issuance, safeguarding-account movements and redemption
  payouts behind EMIGovernor AND the phase table -- never remove
  `:emoney/issue` / `:safeguarding/segregate` / `:redemption/redeem` from
  a governor hard-check or add any of them to a phase's `:auto` set.
- Never weaken or remove the `:interest-or-credit-forbidden` check -- an
  EMI must never pay interest or extend credit against an e-money
  balance (EMD2).
- Treat this as a high-risk domain: add tests for spec-basis, sanctions,
  evidence-completeness, safeguarding-IBAN validity, balance integrity
  and audit logging with every change.
- A new jurisdiction entry in `emi.facts/catalog` MUST cite a real
  official source (`:provenance`) -- do not add a placeholder, and be
  honest about structural differences (see the `"USA"` entry) rather
  than forcing every jurisdiction into the EMD2-style EMI shape.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which governor or phase invariant is affected
- how it was tested
- whether operator or certification docs need updates
