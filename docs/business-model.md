# Open Business Blueprint: cloud-itonami-emi

This repository publishes an OSS business model for operating an
electronic-money-issuance service on itonami.cloud.

## Classification

- Repository name: `cloud-itonami-emi`
- Primary classification: Electronic Money Institution (EMD2/PSD2-style
  e-money issuer), general-purpose across payment use cases
- Activity: e-money issuance and wallet/balance custody
- Served domain: e-money issuance, wallet custody, safeguarding
  confirmation, redemption
- Original implementation context: commissioned to fill a gap found
  during a payment-industry research project (ADR-2607246000) --
  cloud-itonami had banking (`isic-6419`), consumer credit (`isic-6492`)
  and card processing/acquiring (`isic-6619`) actors, but no EMI/e-money-
  issuer actor. Designed alongside the parallel `cloud-itonami-card-
  issuing` (card issuance) and `cloud-itonami-pi` (Payment Institution /
  remittance) actors -- this actor is scoped to avoid overlapping either.

## Customer

Primary customers:

- fintechs and payments platforms that need a governed e-money-issuance
  and wallet-custody execution layer instead of building compliance
  tooling from scratch
- marketplaces and platforms that want to offer branded e-money wallets
  to their users
- corporates running closed-loop or semi-closed-loop prepaid programs
- EMI license holders who want to operate in a new jurisdiction without
  rebuilding a compliance stack from scratch

## Problem

E-money issuance today is either built ad hoc (with no clear, auditable
trail of which spec-basis justified a requirement) or bolted onto a
banking core that quietly lets interest or credit leak onto e-money
balances -- which is exactly what turns an EMI into an unlicensed bank
under EMD2. A customer trusting an EMI with their balance has no way to
verify why an issuance was screened and approved properly, or to prove
after the fact that funds backing their e-money were actually
safeguarded.

## Offer

Operators provide a governed e-money-issuance + wallet-custody tool:

- wallet intake and normalization
- per-jurisdiction evidence checklist, always citing an official source
  (never a fabricated requirement)
- KYC / sanctions screening gate on every customer
- e-money issuance at par value, human-approved (the actor never issues
  alone)
- safeguarding-account confirmation with an independently re-verified
  IBAN checksum (ISO 7064 MOD 97-10)
- redemption on demand, at par, in whole or in part
- a structural, un-overridable governor check that no proposal can ever
  attach interest or credit to an e-money balance
- immutable audit ledger of every draft, hold, and approval

The core promise: the EMI-LLM can draft and check, but it cannot issue,
move safeguarded funds, or redeem unless a human operator -- who holds
the actual license and liability -- approves.

## Revenue

Operators can sell:

- per-issuance / per-redemption execution fee
- jurisdiction-pack licensing: a maintained, spec-cited requirement
  catalog for a specific country, kept current
- managed hosting: monthly subscription per tenant (fintech, platform,
  corporate program)
- KYC/sanctions-screening add-on (integration with a real screening
  provider is the operator's responsibility)
- compliance package: audit export, retention, security review

| Package | Customer | Price shape |
|---|---|---|
| Per-transaction | individual fintech | flat fee per issuance/redemption |
| Jurisdiction pack | payments platform | subscription per country covered |
| Managed tenant | corporate prepaid program | monthly platform fee |
| Operator enablement | new EMI license holder | training + certification |

## Unit Economics

Track these numbers for every operator:

- setup hours per new jurisdiction added to `emi.facts`
- LLM cost per intake/assessment/screening operation
- KYC/sanctions-screening provider cost per customer
- safeguarding-bank integration and reconciliation cost
- human-approval hours per issuance/safeguarding-movement/redemption
- incident and audit hours
- gross margin after infrastructure, screening-provider and support costs

## Open Participation

Anyone may:

- fork the repository
- run the demo
- deploy a self-hosted instance
- submit issues and patches
- publish an additional jurisdiction pack (with a real official
  spec-basis citation)
- create a local operator business

itonami.cloud should require certification -- including proof of the
jurisdiction's actual e-money-institution license -- before listing an
operator as a trusted provider or routing customer leads.

## Operator Trust Levels

| Level | Capability |
|---|---|
| Contributor | patches, docs, issues, examples, jurisdiction packs |
| Self-host operator | runs their own instance with no platform endorsement |
| Certified operator | listed on itonami.cloud after review, including jurisdiction licensing proof |
| Managed operator | may receive leads and operate customer tenants |
| Core maintainer | can approve changes to governor, security and governance |

## Marketplace Metadata

```edn
{:itonami.blueprint/id "cloud-itonami-emi"
 :itonami.blueprint/name "Electronic Money Institution"
 :itonami.blueprint/domain :finance/electronic-money-issuance
 :itonami.blueprint/license "AGPL-3.0-or-later"
 :itonami.blueprint/operator-model :certified-open-business
 :itonami.blueprint/repo "https://github.com/cloud-itonami/cloud-itonami-emi"
 :itonami.blueprint/status :public-oss}
```

## Non-Negotiables

- Do not commit real customer wallets, balances, credentials,
  identification documents or screening results.
- Do not bypass the EMIGovernor for an issuance, safeguarding movement or
  redemption.
- Do not weaken or remove the `:interest-or-credit-forbidden` check.
- Do not add a jurisdiction to `emi.facts` without a real, citable
  official source.
- Do not market an uncertified deployment as an itonami.cloud certified
  operator, and do not operate in a jurisdiction without the
  e-money-institution license that jurisdiction actually requires.
