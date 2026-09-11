# Operator Guide

This guide is for people who want to start an open business from
`cloud-itonami-emi`.

## 1. Fork and Run

```bash
git clone https://github.com/cloud-itonami/cloud-itonami-emi
cd cloud-itonami-emi
kbb -M:dev:test
kbb -M:dev:run
```

The default demo uses synthetic wallets and customers. Production
customer wallets, balances and screening results must stay outside the
repository and be injected through a store adapter.

## 2. Choose an Operating Mode

| Mode | Use when |
|---|---|
| Demo | validating the actor and governor contract |
| Self-host | one operator owns infrastructure and customer data |
| Managed tenant | an operator hosts for a fintech / payments platform |
| Certified operator | itonami.cloud has reviewed license, security and process controls |

## 3. Production Checklist

- confirm you (the operator) hold whatever e-money-institution license
  the target jurisdiction requires -- this software does not grant or
  substitute for one
- replace demo data with a customer-owned store
- configure Datomic Local, kotoba-server or an equivalent durable SSoT
  (see `src/emi/store.kotoba`'s docstring for the `DatomicStore` seam this
  actor is designed to grow into)
- configure the LLM adapter through environment variables or a secret
  manager
- integrate a real KYC/sanctions-screening provider behind
  `emi.emiadvisor`'s `:kyc/screen` path
- integrate a real safeguarding-bank/custodian for the segregated account
  `:safeguarding/segregate` confirms movements into
- extend `emi.facts/catalog` for every jurisdiction you serve, each entry
  citing the jurisdiction's own official e-money-issuance regulator as
  `:provenance`
- run `kbb -M:dev:test`
- run `kbb -M:lint`
- verify audit-ledger export
- document backup and restore
- document incident response
- get written approval for handling customer identification documents
  and safeguarding-account credentials

## 4. Sales Motion

Start with a narrow offer:

1. one jurisdiction, one currency
2. prove the governed evidence-checklist + KYC-screening + issuance flow
3. run one issuance and one redemption through human approval end-to-end
4. export the audit ledger for the customer's own records
5. expand to a second jurisdiction only after the first is repeatable

Avoid selling "any country, any currency" before the jurisdiction pack
and the human-approval workflow for that jurisdiction actually exist and
have been exercised.

## 5. Certification Requirements

itonami.cloud certification should require:

- passing tests and lint on the published version
- proof of the operator's jurisdiction-specific e-money-institution
  license/registration where the jurisdiction requires one
- written data-flow diagram, including where KYC documents and
  safeguarding-account credentials are stored
- backup/restore evidence
- incident contact and response window
- proof that every issuance/safeguarding-movement/redemption passes
  through a human approval step (never bypassed, never auto-committed --
  see README `Actuation`)
- proof that the `:interest-or-credit-forbidden` governor check has not
  been weakened or removed
- proof that real customer identification documents are not stored in
  Git
- customer-facing support terms

## 6. Operator Responsibilities

Operators are responsible for:

- holding the actual e-money-institution license/registration a
  jurisdiction requires
- customer consent and lawful basis for KYC data processing
- the real safeguarding-bank integration (segregated account or
  insurance/guarantee per EMD2 Art. 7)
- the real payout rail for redemptions
- secure infrastructure and tenant isolation
- human approval workflow staffing (someone has to actually review and
  approve each issuance, safeguarding movement and redemption)
- data-retention policy for identification documents
- security updates

The OSS project provides software and an operating blueprint. It does not
make an operator licensed, KYC-compliant, or legally authorized to issue
e-money on anyone's behalf by itself.
