(ns emi.governor
  "EMIGovernor -- the independent compliance layer that earns the
  EMI-LLM the right to commit. The LLM has no notion of e-money law,
  sanctions exposure, EMD2's par-value/no-interest-no-credit invariants,
  whether a safeguarding account's own IBAN actually passes its own ISO
  7064 MOD 97-10 checksum, or when an act stops being a draft and becomes
  a real-world issuance/safeguarding-movement/redemption, so this MUST be
  a separate system able to *reject* a proposal and fall back to HOLD --
  the EMI analog of `cloud-itonami-isic-6910`'s RegistrarGovernor and
  `cloud-itonami-isic-6419`'s Monetary Intermediation Governor.

  Twelve checks, in priority order. The first ten are HARD violations: a
  human approver CANNOT override them (you don't get to approve your way
  past a sanctions hit, a fabricated jurisdiction spec-basis, an attempt
  to attach interest/credit to an e-money balance, an invalid
  safeguarding-account IBAN, or a redemption that exceeds the wallet's
  own balance). The last two are SOFT: they ask a human to look (low
  confidence / actuation), and the human may approve -- but see
  `emi.phase`: for `:stake :actuation` (a real e-money issuance, a real
  safeguarding-account movement, or a real redemption payout) NO phase
  ever allows auto-commit either. Two independent layers agree that
  actuation is always a human call.

    1.  Effect matches op          -- does the proposal's :effect (what
                                       actually gets written to the SSoT
                                       on commit) match the ONE
                                       legitimate effect for the
                                       REQUEST's :op (`op->effect`)?
                                       Checked FIRST for the same reason
                                       `formation.governor`/`banking.
                                       governor` check it first: without
                                       it, an advisor could answer a
                                       harmless-looking
                                       `:jurisdiction/assess` request
                                       with `:effect :emoney/issued`, and
                                       a human approving what looks like
                                       an assessment would silently
                                       trigger REAL e-money issuance with
                                       none of `:emoney/issue`'s own
                                       scrutiny ever run.
    2.  Spec-basis                 -- did the proposal cite an OFFICIAL
                                       source (`emi.facts`), or invent
                                       one?
    3.  Sanctions hold              -- does the customer AT STAKE in this
                                       proposal (the KYC subject itself,
                                       or the wallet's customer for an
                                       issuance/redemption) carry a
                                       sanctions/PEP hit (screened or on
                                       file)?
    4.  KYC incomplete              -- for `:emoney/issue`/`:redemption/
                                       redeem`, has the wallet's customer
                                       actually been screened AND cleared
                                       (`:verdict :clear`)? A never-
                                       screened customer is not a hit
                                       (nil != :hit), so `sanctions-
                                       violations` alone would let
                                       issuance/redemption through with
                                       zero screening ever performed.
    5.  Evidence incomplete         -- for `:emoney/issue`, has the
                                       jurisdiction's required identity-
                                       verification-record/source-of-
                                       funds-record/safeguarding-account-
                                       confirmation/sanctions-screening-
                                       record evidence checklist actually
                                       been satisfied on file?
    6.  Interest-or-credit forbidden -- EMD2's single most important
                                       structural rule for this domain:
                                       an EMI must NEVER attach interest
                                       or extend credit against an
                                       e-money balance (that would make
                                       it a deposit-taking bank, which is
                                       `cloud-itonami-isic-6419`'s
                                       domain, not this one). Evaluated
                                       UNCONDITIONALLY on ANY op's
                                       proposal `:value` -- a proposal
                                       carrying a non-zero
                                       `:interest-rate` or
                                       `:credit-extended` is rejected no
                                       matter how it got there.
    7.  Insufficient balance         -- for `:redemption/redeem`,
                                       INDEPENDENTLY recompute whether
                                       the requested redemption amount
                                       exceeds the wallet's own on-file
                                       balance -- needs no proposal
                                       inspection beyond the amount
                                       itself, since the balance is a
                                       permanent ground-truth field
                                       already on the wallet.
    8.  IBAN checksum invalid        -- for `:safeguarding/segregate`,
                                       INDEPENDENTLY recompute whether
                                       the proposed safeguarding
                                       account's own IBAN passes ISO 7064
                                       MOD 97-10 (`emi.registry/iban-
                                       checksum-invalid?`) -- the SAME
                                       real algorithm `banking.registry`
                                       established, reused rather than
                                       re-derived.
    9.  Post-closure intake blocked  -- `:wallet/intake` is the ONLY op
                                       any phase ever puts in its `:auto`
                                       set (pre-issuance customer/wallet
                                       data entry auto-commits with NO
                                       human approval). Once a wallet is
                                       `:closed` (fully redeemed), intake
                                       is blocked outright -- there is
                                       nothing left to onboard.
   10.  Intake fabrication           -- even a PRE-issuance intake patch
                                       is not a blank cheque: it may
                                       never set `:balance`/`:emoney-
                                       record-id`/`:safeguarded-amount`/
                                       `:safeguarding-iban` (those are
                                       assigned ONLY by a real issuance/
                                       safeguarding/redemption), never
                                       set `:status` to `:active`/
                                       `:closed` (terminal/transitional
                                       actuation states reached ONLY via
                                       the internal wallet-patch a real
                                       actuation applies itself), and its
                                       own patch `:id` (if present) must
                                       match the request's `:subject`.
   11.  Confidence floor             -- LLM confidence below threshold ->
                                       escalate.
   12.  Actuation gate               -- `:stake :actuation` -> always
                                       escalate; never auto, at any
                                       phase (structural, not a policy
                                       toggle).

  One more guard, already-closed-wallet prevention, is enforced but not
  numbered above because it needs no upstream comparison at all --
  `already-closed-violations` refuses to issue e-money into, or redeem
  from, a wallet whose dedicated `:status` fact is already `:closed`
  (never inferred from anything else)."
  (:require [emi.facts :as facts]
            [emi.registry :as registry]
            [emi.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  `:actuation` = a real e-money issuance, a real safeguarding-account
  movement, or a real redemption payout -- three distinct real-world
  acts, one shared stake keyword (the same shape `formation.governor`
  uses for filing/amend/dissolve)."
  #{:actuation})

;; ----------------------------- checks -----------------------------

(def op->effect
  "The ONE legitimate `:effect` a proposal may declare for each op --
  `emi.operation/commit-record` takes `:effect` straight from the
  (untrusted) advisor proposal with no cross-check of its own, so this
  table is the only thing standing between 'the request says
  :jurisdiction/assess' and 'the SSoT mutation that actually runs is
  :emoney/issued'."
  {:wallet/intake         :wallet/upsert
   :jurisdiction/assess   :assessment/set
   :kyc/screen            :kyc/set
   :emoney/issue          :emoney/issued
   :safeguarding/segregate :safeguarding/segregated
   :redemption/redeem     :redemption/redeemed})

(defn- effect-mismatch-violations
  [{:keys [op]} proposal]
  (when-let [expected (op->effect op)]
    (when (not= expected (:effect proposal))
      [{:rule :effect-mismatch
        :detail (str "op " op " の提案は :effect " expected
                     " のはずが実際には " (:effect proposal) " になっている")}])))

(defn- spec-basis-violations
  "A `:jurisdiction/assess`, `:emoney/issue`, `:safeguarding/segregate` or
  `:redemption/redeem` proposal with no spec-basis citation is a HARD
  violation -- never invent a jurisdiction's e-money-issuance law."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :emoney/issue :safeguarding/segregate :redemption/redeem} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- customer-id-at-stake
  "Which customer-id does THIS proposal actually put in front of the
  e-money ledger? `:kyc/screen`'s subject IS the customer-id directly.
  `:emoney/issue`/`:redemption/redeem`'s subject is the WALLET, so the
  customer at stake is that wallet's `:customer-id`. This is the single
  place both `sanctions-violations` and `kyc-completeness-violations`
  consult, so 'which ops screen the customer' cannot drift between the
  two checks."
  [{:keys [op subject]} st]
  (cond
    (= op :kyc/screen) subject
    (contains? #{:emoney/issue :redemption/redeem} op) (:customer-id (store/wallet st subject))
    :else nil))

(defn- sanctions-violations
  "A sanctions/PEP hit on the customer at stake -- screened in THIS
  proposal or already on file in the store -- is a HARD, un-overridable
  hold."
  [request proposal st]
  (let [hit-in-proposal? (= :hit (get-in proposal [:value :verdict]))
        cust-id (customer-id-at-stake request st)
        hit-on-file? (and cust-id (= :hit (:verdict (store/kyc-of st cust-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :sanctions-hit
        :detail "制裁/PEPリスト一致のある顧客に対する電子マネー発行/償還提案は進められない"}])))

(defn- kyc-completeness-violations
  "For `:emoney/issue`/`:redemption/redeem`, the wallet's customer must
  have been KYC-screened AND cleared (`:verdict :clear`) -- HARD,
  un-overridable. `sanctions-violations` alone is not enough: a customer
  who was simply never screened has a nil verdict, and nil is not :hit."
  [{:keys [op] :as request} st]
  (when (contains? #{:emoney/issue :redemption/redeem} op)
    (let [cust-id (customer-id-at-stake request st)]
      (when-not (= :clear (:verdict (store/kyc-of st cust-id)))
        [{:rule :kyc-incomplete
          :detail "顧客のKYCスクリーニング(:clear)が完了していない状態での電子マネー発行/償還提案"}]))))

(defn- evidence-incomplete-violations
  "For `:emoney/issue`, the jurisdiction's required evidence must
  actually be satisfied -- do not trust the advisor's self-reported
  confidence alone."
  [{:keys [op subject]} st]
  (when (= op :emoney/issue)
    (let [w (store/wallet st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction w) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(本人確認記録/資金源確認記録/保全措置確認記録/制裁リストスクリーニング記録等)が充足していない状態での電子マネー発行提案"}]))))

(defn- interest-or-credit-violations
  "EMD2's central structural rule for this domain: e-money must NEVER
  carry interest or credit -- evaluated UNCONDITIONALLY on ANY op's
  proposal `:value`, not scoped to a specific op, so a would-be
  interest-bearing or credit-extending proposal is rejected no matter
  which op it rode in on."
  [_request proposal]
  (let [v (:value proposal)]
    (when (or (some-> (:interest-rate v) (not= 0))
              (some-> (:credit-extended v) (not= 0)))
      [{:rule :interest-or-credit-forbidden
        :detail "EMD2は電子マネー残高への金利付与・与信供与を禁止している(e-money balance must never carry interest or credit; that would make this a deposit-taking bank, not an EMI)"}])))

(defn- insufficient-balance-violations
  "For `:redemption/redeem`, INDEPENDENTLY recompute whether the
  requested amount exceeds the wallet's own on-file balance."
  [{:keys [op subject]} proposal st]
  (when (= op :redemption/redeem)
    (let [w (store/wallet st subject)
          amount (get-in proposal [:value :amount])]
      (when (or (nil? amount) (not (number? amount)) (<= amount 0)
                (> amount (:balance w 0)))
        [{:rule :insufficient-balance
          :detail (str subject " の残高(" (:balance w 0) ")を超える償還提案")}]))))

(defn- iban-checksum-invalid-violations
  "For `:safeguarding/segregate`, INDEPENDENTLY recompute whether the
  proposed safeguarding account's own IBAN passes ISO 7064 MOD 97-10 via
  `emi.registry/iban-checksum-invalid?` -- needs no store lookup at all,
  since its input is the proposal's own declared IBAN."
  [{:keys [op]} proposal]
  (when (= op :safeguarding/segregate)
    (let [iban (get-in proposal [:value :iban])]
      (when (registry/iban-checksum-invalid? {:iban iban})
        [{:rule :iban-checksum-invalid
          :detail (str "保全口座IBAN(" iban ")がISO 7064 MOD 97-10検査に不合格")}]))))

(defn- post-closure-intake-violations
  "`:wallet/intake` exists for PRE-issuance customer/wallet data entry.
  Once a wallet is `:closed` (fully redeemed), allowing intake to keep
  touching it would let a fully wound-down wallet be silently reopened
  with ZERO governor scrutiny."
  [{:keys [op subject]} st]
  (when (= op :wallet/intake)
    (let [w (store/wallet st subject)]
      (when (= :closed (:status w))
        [{:rule :post-closure-intake-blocked
          :detail "解約済みウォレットへの intake 経由の変更は禁止"}]))))

(defn- intake-fabrication-violations
  "HARD: `:wallet/intake` is the ONE op any phase ever auto-commits
  (`emi.phase`) -- zero human approval, ever. Its patch content needs
  structural limits: it may never set `:balance`/`:emoney-record-id`/
  `:safeguarded-amount`/`:safeguarding-iban` (assigned ONLY by a real
  actuation), never set `:status` to `:active`/`:closed` (reached ONLY
  via the internal wallet-patch a real actuation applies itself), and its
  own patch `:id` (if present) must equal the request's `:subject`."
  [{:keys [op subject]} proposal]
  (when (= op :wallet/intake)
    (let [patch (:value proposal)]
      (cond-> []
        (some #(contains? patch %) [:balance :emoney-record-id :safeguarded-amount :safeguarding-iban])
        (conj {:rule :intake-forbidden-field
               :detail "intake で balance/emoney-record-id/safeguarded-amount/safeguarding-iban を設定することはできない(実際のissue/safeguard/redeemでのみ発行される)"})
        (contains? #{:active :closed} (:status patch))
        (conj {:rule :intake-forbidden-status
               :detail "intake で :status を :active/:closed にすることはできない(実際のissue/redeemでのみ到達する状態)"})
        (and (contains? patch :id) (not= (:id patch) subject))
        (conj {:rule :intake-subject-mismatch
               :detail "patch の :id がリクエストの subject と一致しない"})))))

(defn- already-closed-violations
  "For `:emoney/issue`/`:redemption/redeem`, refuses to touch a wallet
  whose dedicated `:status` fact is already `:closed` (never a value
  inferred from balance alone)."
  [{:keys [op subject]} st]
  (when (contains? #{:emoney/issue :redemption/redeem} op)
    (let [w (store/wallet st subject)]
      (when (= :closed (:status w))
        [{:rule :wallet-already-closed
          :detail (str subject " は既に解約(closed)済みのウォレット")}]))))

(defn check
  "Censors an EMI-LLM proposal against the governor rules. Returns
  {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (effect-mismatch-violations request proposal)
                           (spec-basis-violations request proposal)
                           (sanctions-violations request proposal st)
                           (kyc-completeness-violations request st)
                           (evidence-incomplete-violations request st)
                           (interest-or-credit-violations request proposal)
                           (insufficient-balance-violations request proposal st)
                           (iban-checksum-invalid-violations request proposal)
                           (post-closure-intake-violations request st)
                           (intake-fabrication-violations request proposal)
                           (already-closed-violations request st)))
        conf (:confidence proposal 0.0)
        low? (or (< conf confidence-floor) (> conf 1.0))
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
