# Governance

`cloud-itonami-emi` is an OSS open-business blueprint. Governance covers
both code and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the EMI-LLM cannot directly issue e-money, move safeguarded funds, or
  pay out a redemption.
- EMIGovernor remains independent of the advisor.
- hard governor violations (fabricated spec-basis, sanctions hit,
  interest/credit on an e-money balance, an invalid safeguarding-account
  IBAN, insufficient balance) cannot be overridden by human approval.
- `:emoney/issue`, `:safeguarding/segregate` and `:redemption/redeem` are
  never members of any phase's `:auto` set.
- every commit, hold and approval path is auditable.
- real customer identification documents and screening results stay
  outside Git.
- no jurisdiction is added to `emi.facts` without a real, citable
  official source.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, actuation invariant, public business model, operator
certification or license should add or update an ADR.
