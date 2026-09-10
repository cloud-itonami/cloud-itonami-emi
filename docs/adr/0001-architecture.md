# ADR 0001: cloud-itonami-emi architecture

## Status

Accepted.

## Context

This workspace's `cloud-itonami` fleet covers ~750+ ISIC-industry-code /
financial-license-type business-automation actors, each an LLM-advisor ⊣
independent-Governor pair (`langgraph-clj` StateGraph, portable `.cljc`)
with real destructive/regulated actions hard-gated behind human approval.
A payment-industry research project
(`90-docs/adr/2607246000-adult-content-payment-processor-banking-
jurisdiction-research.edn` in the `com-junkawasaki/root` superproject)
found the fleet already had banking (`cloud-itonami-isic-6419`), consumer
credit (`cloud-itonami-isic-6492`) and card-processing/acquiring
(`cloud-itonami-isic-6619`) actors, but **no Electronic Money Institution
(EMI) actor existed anywhere** -- a real gap, since e-money issuance is a
distinct, separately-licensed regulatory activity under EMD2/PSD2 (EU)
and equivalent regimes elsewhere, not a subset of banking, card issuance,
or payment-institution remittance.

This repository fills that gap. It is **general-purpose** -- any
legitimate payment use case (wallet products, prepaid programs,
marketplace-branded e-money, etc.) -- not specific to the research
project's originating adult-content-payments motivation; that ADR is the
reason this actor exists, not its scope.

Two sibling actors are being built alongside this one and are
deliberately kept non-overlapping (see Non-goals below):
`cloud-itonami-card-issuing` (real card issuance to a physical/virtual
card program) and `cloud-itonami-pi` (Payment Institution services --
payment execution/remittance for third-party merchants).

## Decision

Build `cloud-itonami-emi` following the established cloud-itonami actor
pattern exactly, targeting the same maturity level as
`cloud-itonami-isic-6910` (Global Incorporation Actor): a real actor
loop (advisor/governor/phase/registry/store/facts/operation), MemStore-
only persistence, no external integrations wired up yet, every
destructive/regulated action hard-gated behind human approval.

### Domain scope (EMD2/PSD2 shape)

In scope:

- issuing electronic money -- a prepaid claim stored electronically,
  redeemable at par (`:emoney/issue`)
- maintaining e-money balances/wallets for customers (`emi.store`'s
  `wallet` entity)
- safeguarding client funds received in exchange for e-money issued --
  segregated account or insurance/guarantee, EMD2 Art. 7
  (`:safeguarding/segregate`)
- redemption on demand, at par, in whole or in part, EMD2 Art. 11
  (`:redemption/redeem`)

### Non-goals (explicitly out of scope)

1. **Extending credit against e-money.** EMD2 prohibits interest and
   credit on e-money balances -- this is the single most important
   structural rule of the whole domain, because an EMI that pays
   interest or lends against float is functionally operating as an
   unlicensed bank. Enforced by `emi.governor`'s
   `:interest-or-credit-forbidden` check, evaluated UNCONDITIONALLY on
   any op's proposal `:value`, HARD (un-overridable by human approval).
2. **Deposit-taking in the banking-license sense.** That is
   `cloud-itonami-isic-6419` (Community Monetary Intermediation)'s
   domain.
3. **Real card issuance to a physical/virtual card program.** That is
   the parallel `cloud-itonami-card-issuing` actor's domain -- this
   actor does not model card products, BIN sponsorship, or card-network
   scheme rules.
4. **Real payment execution/remittance as a service to third-party
   merchants.** That is the parallel `cloud-itonami-pi` (Payment
   Institution) actor's domain -- this actor does not model merchant
   acquiring, payment-initiation services, or account-information
   services.
5. **Consumer credit underwriting.** That is `cloud-itonami-isic-6492`'s
   domain.

## Governor gate design

`emi.governor/check` runs twelve checks in priority order (see the ns
docstring for the full numbered list and rationale for each). Ten are
HARD (un-overridable by a human approver):

1. effect-matches-op (`op->effect` table) -- prevents a mismatched,
   higher-stakes effect riding in on a harmless-looking request
2. spec-basis -- no fabricated jurisdiction e-money-issuance law
3. sanctions hold -- customer at stake (KYC subject, or wallet's
   customer for issuance/redemption)
4. KYC incomplete -- issuance/redemption require the customer to be
   actually screened AND cleared, not merely unscreened (nil != hit)
5. evidence incomplete -- issuance requires the jurisdiction's required
   evidence checklist to actually be satisfied on file
6. **interest-or-credit forbidden** -- the EMI-specific structural rule
   (EMD2), evaluated unconditionally on any op
7. **insufficient balance** -- redemption amount independently
   recomputed against the wallet's own on-file balance
8. **IBAN checksum invalid** -- safeguarding-account IBAN independently
   re-verified via ISO 7064 MOD 97-10 (the same real algorithm
   `banking.registry`/`cloud-itonami-isic-6419` established, ported
   verbatim rather than re-derived)
9. post-closure intake blocked -- a fully redeemed (`:closed`) wallet
   cannot be reopened via the one auto-committing op
10. intake fabrication -- `:wallet/intake` cannot set `:balance`/
    `:emoney-record-id`/`:safeguarded-amount`/`:safeguarding-iban`, set
    terminal `:status`, or target a different wallet than its declared
    subject

Two more are SOFT (a human may approve past them): confidence floor, and
the actuation gate (`:stake :actuation` always escalates). One more guard
(already-closed-wallet, double-touch prevention) is enforced alongside
the ten HARD checks off a dedicated `:status` fact, never inferred.

## R0 maturity boundaries

- **Store**: `MemStore` only (`emi.store`). No `DatomicStore`/
  kotoba-server backend yet -- the `Store` protocol is designed so that
  addition is a configuration change, not a rewrite, following
  `formation.store`'s `DatomicStore` pattern in
  `cloud-itonami-isic-6910` when this actor is ready to grow past R0.
- **Advisor**: `mock-advisor` (deterministic) is the default;
  `llm-advisor` (real `langchain.model/ChatModel` inference) exists and
  is tested (`test/emi/llm_advisor_test.kotoba`) but no production LLM
  provider is wired up.
- **No external integrations**: no real KYC/sanctions-screening
  provider, no real safeguarding-bank/custodian integration, no real
  payout rail. All of these are operator responsibilities documented in
  `docs/operator-guide.md`'s Production Checklist.
- **No corporate-intelligence cross-reference** (unlike
  `cloud-itonami-isic-6910`'s optional `formation.corporate-intel` wiring
  into `cloud-itonami-isic-8291`) -- out of scope for this actor's R0;
  may be added later following the same injected-fn pattern if needed.
- **8 jurisdictions seeded** in `emi.facts/catalog` (GBR, DEU, FRA, LTU,
  IRL, JPN, SGP, and a structural-difference note for USA) -- a starting
  catalog, not a survey of all ~194 jurisdictions.

## Relation to sibling actors

| Actor | Domain | Relationship |
|---|---|---|
| `cloud-itonami-isic-6419` | Community Monetary Intermediation (banking) | deposit-taking is explicitly out of this actor's scope; this actor's IBAN-checksum algorithm is ported verbatim from `banking.registry` |
| `cloud-itonami-isic-6492` | Consumer credit | credit underwriting is explicitly out of this actor's scope; this actor's governor structurally forbids credit on e-money balances |
| `cloud-itonami-isic-6619` | Card processing / acquiring | pre-existing sibling; this actor does not issue cards |
| `cloud-itonami-card-issuing` (parallel) | Real card issuance | non-overlapping by design -- this actor does not model card products |
| `cloud-itonami-pi` (parallel) | Payment Institution (remittance/execution) | non-overlapping by design -- this actor does not model merchant payment execution |
| `cloud-itonami-isic-6910` | Company formation | closest architectural analog for R0 maturity target (same shape: advisor/governor/phase/registry/store/facts/operation, MemStore-only, actuation always human) |

## Testing

60 tests / 188 assertions across `test/emi/*_test.clj`: facts coverage,
IBAN-checksum conformance (including corrupted-checksum detection),
issuance/safeguarding/redemption record construction and append-only
history, `MemStore` contract, phase-table structural invariants (no
actuation op ever auto-commits, at any phase, present or future), the
full governor contract (every HARD/SOFT check exercised both via direct
`governor/check` calls and end-to-end through the real `emi.operation`
actor graph, including the human-approval interrupt/resume path), and
the real-LLM advisor (parsed defensively, fully re-censored by the
governor, including a proposal that tries to smuggle a non-zero
`:interest-rate` past the advisor layer). `clojure -M:lint` (clj-kondo)
passes with zero warnings.

## Repository

https://github.com/cloud-itonami/cloud-itonami-emi
