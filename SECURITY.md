# Security Policy

This project handles electronic-money issuance workflows, including
customer identification, KYC/sanctions-screening results, safeguarding-
account IBANs and wallet balances. Treat vulnerabilities as potentially
high impact even when the demo data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real customer, wallet, balance or identification-document exposure
- authorization bypass
- EMIGovernor bypass
- a path that lets `:emoney/issue`, `:safeguarding/segregate` or
  `:redemption/redeem` auto-commit at any phase
- a path that lets an e-money balance carry interest or credit
- audit-ledger tampering
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on customer data, governor enforcement, actuation invariant,
  safeguarding integrity or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real customer/wallet/balance data outside this repository.
- Run governor and phase tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
- Never wire `:emoney/issue`, `:safeguarding/segregate` or
  `:redemption/redeem` to run without a human approval step, regardless
  of confidence or phase.
- Never remove or weaken the `:interest-or-credit-forbidden` governor
  check -- an EMI that pays interest or extends credit against e-money
  is no longer operating as an EMI.
