# cloud-itonami-emi

[![ci](https://github.com/cloud-itonami/cloud-itonami-emi/actions/workflows/ci.yml/badge.svg)](https://github.com/cloud-itonami/cloud-itonami-emi/actions/workflows/ci.yml)

Open Business Blueprint for an **Electronic Money Institution (EMI)**:
e-money issuance and wallet/balance custody. This repository publishes a
governed e-money-issuance actor as an OSS business that any qualified,
licensed operator can fork, deploy, run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, in-mem checkpoints) -- the same actor pattern as
[`robotaxi-actor`](https://github.com/com-junkawasaki/robotaxi-actor)
(AR1 ⊣ SafetyGovernor), [`cloud-itonami-isic-6910`](https://github.com/cloud-itonami/cloud-itonami-isic-6910)
(Registrar-LLM ⊣ RegistrarGovernor) and
[`cloud-itonami-isic-6419`](https://github.com/cloud-itonami/cloud-itonami-isic-6419)
(BankingOps-LLM ⊣ Monetary Intermediation Governor). Here it is
**EMI-LLM ⊣ EMIGovernor**.

> **Why an actor layer at all?** An LLM is great at drafting a per-
> jurisdiction evidence checklist, normalizing wallet intake or flagging
> a thin KYC file -- but it has **no notion of which e-money-issuance
> license regime is real, no legal standing, no ability to tell that
> attaching interest to a balance turns an EMI into an unlicensed bank,
> and no business being the one that decides a customer's e-money
> balance actually changes today**. Letting it issue, move safeguarded
> funds, or pay out a redemption directly invites fabricated licensing
> requirements, laundering sanctioned parties into a wallet, silently
> paying interest on float (which EMD2 forbids), and unaccountable
> liability for whoever runs it. This project seals the EMI-LLM into a
> single node and wraps it with an independent **EMIGovernor**, a human
> **approval workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor is **general-purpose** -- it exists to fill a real gap in this
workspace's payments coverage (banking/`isic-6419`, consumer credit/
`isic-6492` and card processing/acquiring/`isic-6619` all existed; no
EMI/e-money-issuer actor did), not to serve any single vertical. It
drafts and governs an e-money issuance + wallet-custody workflow. It
does **not**, by itself, hold an e-money-institution license in any
jurisdiction, and it does not claim to. Whoever deploys and operates a
live instance (a licensed EMI, a fintech's compliance team, a payments
platform) supplies the jurisdiction-specific license, the real KYC/AML
program, the real safeguarding-bank integration and the real ledger/core-
banking rail, and bears that jurisdiction's liability -- the software
supplies the governed, spec-cited, audited execution scaffold so that
operator does not have to build the compliance layer from scratch for
every new market.

### In scope (per EU EMD2/PSD2, the reference regulatory shape)

- issuing electronic money -- a prepaid claim stored electronically,
  redeemable at par
- maintaining e-money balances/wallets for customers
- safeguarding client funds received in exchange for e-money issued
  (segregated account, EMD2 Art. 7)
- redemption on demand, at par value, in whole or in part (EMD2 Art. 11)

### Explicitly out of scope (non-goals)

- **extending credit against e-money.** EMD2 prohibits interest and
  credit on e-money balances -- an EMI that pays interest or lends
  against float is not operating as an EMI, it is operating as an
  unlicensed bank. `emi.governor`'s `:interest-or-credit-forbidden` check
  enforces this UNCONDITIONALLY, on any op, and cannot be overridden by
  human approval.
- **deposit-taking in the banking-license sense.** That is
  [`cloud-itonami-isic-6419`](https://github.com/cloud-itonami/cloud-itonami-isic-6419)'s
  domain (Community Monetary Intermediation), not this one.
- **real card issuance to a physical/virtual card program.** A separate
  sibling actor, `cloud-itonami-card-issuing`, covers that -- this actor
  does not overlap with it.
- **real payment execution/remittance as a service to third-party
  merchants.** A separate sibling actor, `cloud-itonami-pi` (Payment
  Institution), covers that -- this actor does not overlap with it.
- **consumer credit underwriting.** That is
  [`cloud-itonami-isic-6492`](https://github.com/cloud-itonami)'s domain.

### Actuation

**A real e-money issuance, a real safeguarding-account movement, or a
real redemption payout is never autonomous, at any phase, by
construction.** Two independent layers enforce this (`emi.governor`'s
`:actuation` high-stakes gate and `emi.phase`'s phase table, which never
puts `:emoney/issue`, `:safeguarding/segregate` or `:redemption/redeem`
in any phase's `:auto` set) -- see `emi.phase`'s docstring and
`test/emi/phase_test.clj`'s `actuation-never-auto-at-any-phase`. The
actor may draft, check, screen and recommend; a human operator is always
the one who actually issues, moves safeguarded funds, and pays out a
redemption.

**`:wallet/intake` is the one op that DOES auto-commit** (it's the only
member of any phase's `:auto` set -- pre-issuance customer/wallet data
entry needs to be fast, not gated). To keep that from becoming a backdoor
around everything above, the governor blocks intake outright once a
wallet is `:closed` (fully redeemed), and it structurally forbids an
intake patch from setting `:balance`/`:emoney-record-id`/`:safeguarded-
amount`/`:safeguarding-iban` or the terminal `:active`/`:closed` status
-- every real e-money change must go through `:emoney/issue`/
`:safeguarding/segregate`/`:redemption/redeem` instead, which carry the
full spec-basis + KYC + evidence + human-approval gate.

## The core contract

```
customer/wallet intake + jurisdiction facts (emi.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────┐
   │   EMI-LLM     │ ─────────────▶ │    EMIGovernor      │  (independent system)
   │   (sealed)    │  + citations   │ spec-basis · KYC ·  │
   └──────────────┘                 │ no-interest/credit  │
                             commit ◀┤ IBAN checksum ·     ├──▶ hold (fabricated law;
                                 │   │ balance integrity   │      sanctions hit; interest
                           record + ledger  └──────────────┘      fabrication; invalid IBAN;
                                                escalate ─▶ 人間承認  insufficient balance;
                                              (ALWAYS for actuation)   un-overridable)
```

**The EMI-LLM never issues, safeguards or redeems a record the
EMIGovernor would reject, and never actuates without a human sign-off.**
Hard violations (fabricated jurisdiction requirements / sanctions hit /
interest-or-credit fabrication / invalid safeguarding IBAN / insufficient
balance) force **hold** and *cannot* be approved past; a clean actuation
proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean wallet through intake -> assessment -> KYC -> issuance -> safeguarding -> redemption, plus five HARD-hold cases
clojure -M:dev:test    # governor contract · phase invariants · IBAN checksum conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Open business

This repository is not only source code. It is a public, forkable business
model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, EMIGovernor, e-money issuance/safeguarding/redemption draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an open
business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Layout

| File | Role |
|---|---|
| `src/emi/store.cljc` | **Store** protocol -- `MemStore` (R0; MemStore-only at this maturity stage, see ADR-2607247000) + append-only audit ledger + e-money-record history |
| `src/emi/registry.cljc` | ISO 7064 MOD 97-10 safeguarding-account IBAN checksum (ported from `banking.registry`) + issuance/safeguarding/redemption draft records |
| `src/emi/facts.cljc` | Per-jurisdiction e-money-issuance requirement catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/emi/emiadvisor.cljc` | **EMI-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/KYC/issuance/safeguarding/redemption proposals |
| `src/emi/governor.cljc` | **EMIGovernor** -- effect-matches-op · spec-basis · sanctions hold · KYC-complete · evidence-complete · no-interest/credit · insufficient-balance · IBAN checksum · post-closure-intake-block · intake-fabrication · already-closed · confidence floor · actuation gate |
| `src/emi/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess/screen → supervised (actuation always human) |
| `src/emi/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/emi/sim.cljc` | demo driver |
| `test/emi/*_test.clj` | governor contract · phase invariants · IBAN checksum conformance · facts coverage · store contract · real-LLM advisor (mock-model) |

## Jurisdiction coverage (honest)

`emi.facts/coverage` reports how many requested jurisdictions actually
have an official spec-basis in `emi.facts/catalog` -- currently 8 seeded
(GBR, DEU, FRA, LTU, IRL, JPN, SGP, and a structural-difference entry for
USA, which has no federal EMI-license category at all -- see the catalog
entry's own note) out of ~194 jurisdictions worldwide. This is a starting
catalog to prove the governor contract end-to-end, not a claim of global
coverage. Adding a jurisdiction is additive: one map entry in
`emi.facts/catalog`, citing a real official source -- never fabricate a
jurisdiction's requirements to make coverage look bigger.

## License

Code and implementation templates are AGPL-3.0-or-later.
