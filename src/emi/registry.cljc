(ns emi.registry
  "Pure-function e-money issuance / safeguarding / redemption record
  construction -- an append-only e-money-institution book-of-record
  draft.

  Like `cloud-itonami-isic-6419`'s `banking.registry`, this domain DOES
  have a single international check-digit standard for the account that
  matters most here -- the SAFEGUARDING account an EMI must hold client
  funds in per EMD2 Art. 7 (segregated account or insurance/guarantee) is
  overwhelmingly identified by an IBAN (ISO 13616), whose own check
  digits are defined by ISO 7064 MOD 97-10. `iban-checksum-invalid?`
  below is the SAME real algorithm `banking.registry/iban-checksum-
  invalid?` implements (ported verbatim, not re-derived and not a
  fabricated placeholder) -- this actor independently re-verifies the
  safeguarding account's own IBAN before any `:safeguarding/segregate`
  proposal can commit (see `emi.governor`), the same 'the identifier
  proves or disproves itself' discipline banking.registry established.

  Every OTHER reference number this actor issues (issuance/safeguarding/
  redemption record IDs) has no such single international check-digit
  standard -- every jurisdiction's e-money regulator assigns its own
  reference format, the same honest, non-fabricating discipline
  `emi.facts` uses; this namespace does NOT invent one for those, it
  builds a jurisdiction-scoped sequence number instead.

  This namespace is pure data + pure functions -- no I/O, no network call
  to any real core-payments/safeguarding-bank system. It builds the
  RECORD an EMI operator would keep, not the act of issuing e-money,
  moving safeguarded funds, or paying out a redemption itself (that is
  `emi.operation`'s `:emoney/issue`/`:safeguarding/segregate`/
  `:redemption/redeem`, always human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  EMI operator's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

;; ----------------------------- ISO 7064 MOD 97-10 IBAN checksum -----------------------------
;; Ported verbatim from `banking.registry` (cloud-itonami-isic-6419) -- see
;; ns docstring for why this is a shared, real algorithm rather than a
;; per-actor reimplementation.

(def ^:private digit-chars (set "0123456789"))
(def ^:private alphabet "ABCDEFGHIJKLMNOPQRSTUVWXYZ")
(def ^:private digit-value
  {"0" 0 "1" 1 "2" 2 "3" 3 "4" 4 "5" 5 "6" 6 "7" 7 "8" 8 "9" 9})

(defn- char->digits
  "ISO 7064 MOD 97-10 letter substitution: A=10 .. Z=35, digits pass
  through unchanged, as decimal-string fragments. Implemented via
  portable `clojure.string` lookups (no JVM-only `Character` interop) so
  this namespace stays a real `.cljc`."
  [c]
  (if (contains? digit-chars c)
    (str c)
    (str (+ 10 (str/index-of alphabet (str c))))))

(defn- iban-numeric-string
  "Move the first 4 characters to the end, then substitute letters for
  digits per ISO 7064 MOD 97-10 -- the standard IBAN validation
  rearrangement."
  [iban]
  (let [cleaned (str/replace (str/upper-case iban) #"\s" "")
        rearranged (str (subs cleaned 4) (subs cleaned 0 4))]
    (apply str (map char->digits rearranged))))

(defn- mod-97
  "Remainder of `numeric-string` modulo 97, computed digit-by-digit so no
  bignum library is required -- the standard streaming-mod-97 technique."
  [numeric-string]
  (reduce (fn [acc i]
            (mod (+ (* acc 10) (digit-value (subs numeric-string i (inc i)))) 97))
          0
          (range (count numeric-string))))

(defn iban-checksum-invalid?
  "Does `safeguarding-account`'s own `:iban` fail ISO 7064 MOD 97-10
  validation? A valid IBAN's rearranged numeric form is congruent to 1
  mod 97 -- any other remainder (or a non-conforming shape: not 15-34
  chars, doesn't start with 2 letters + 2 digits) means the IBAN is
  invalid. A pure ground-truth check against the safeguarding account's
  own `:iban` field -- no upstream comparison or second field needed."
  [{:keys [iban]}]
  (or (nil? iban)
      (not (re-matches #"[A-Za-z]{2}\d{2}[A-Za-z0-9]{11,30}" (str/replace (str iban) #"\s" "")))
      (not= 1 (mod-97 (iban-numeric-string iban)))))

;; ----------------------------- e-money records -----------------------------

(defn register-issuance
  "Validate + construct an e-money ISSUANCE registration DRAFT -- crediting
  a customer wallet with e-money at PAR VALUE against `amount` of received
  funds (EMD2 Art. 11: e-money must be issued at par, immediately, upon
  receipt of funds). Pure function -- does not touch any real ledger; it
  builds the RECORD an EMI operator would keep."
  [wallet-id jurisdiction amount currency sequence]
  (when-not (and wallet-id (not= wallet-id ""))
    (throw (ex-info "issuance: wallet_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "issuance: jurisdiction required" {})))
  (when-not (and currency (not= currency ""))
    (throw (ex-info "issuance: currency required" {})))
  (when-not (number? amount)
    (throw (ex-info "issuance: amount required" {})))
  (when (<= amount 0)
    (throw (ex-info "issuance: amount must be > 0" {})))
  (when (< sequence 0)
    (throw (ex-info "issuance: sequence must be >= 0" {})))
  (let [record-number (str (str/upper-case jurisdiction) "-EMI-" (zero-pad sequence 8))
        record {"record_id" record-number
                "kind" "issuance-draft"
                "wallet_id" wallet-id
                "jurisdiction" jurisdiction
                "amount" amount
                "currency" currency
                "par_value" true
                "immutable" true}]
    {"record" record "record_number" record-number
     "certificate" (unsigned-certificate "EmoneyIssuanceCertificate" record-number record-number)}))

(defn register-safeguarding
  "Validate + construct a SAFEGUARDING registration DRAFT -- confirming
  that `amount` of client funds backing outstanding e-money has been
  moved to / held in the segregated safeguarding account `iban` (EMD2
  Art. 7: safeguard funds received in exchange for e-money issued, via a
  segregated account or an equivalent insurance/guarantee). Pure function
  -- does not touch any real custodial/banking system; `emi.governor`
  independently re-verifies `iban`'s own ISO 7064 MOD 97-10 checksum
  before this is ever allowed to commit."
  [wallet-id jurisdiction iban amount sequence]
  (when-not (and wallet-id (not= wallet-id ""))
    (throw (ex-info "safeguarding: wallet_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "safeguarding: jurisdiction required" {})))
  (when-not (and iban (not= iban ""))
    (throw (ex-info "safeguarding: iban required" {})))
  (when-not (number? amount)
    (throw (ex-info "safeguarding: amount required" {})))
  (when (<= amount 0)
    (throw (ex-info "safeguarding: amount must be > 0" {})))
  (when (< sequence 0)
    (throw (ex-info "safeguarding: sequence must be >= 0" {})))
  (let [record-number (str (str/upper-case jurisdiction) "-SFG-" (zero-pad sequence 8))
        record {"record_id" record-number
                "kind" "safeguarding-draft"
                "wallet_id" wallet-id
                "jurisdiction" jurisdiction
                "iban" iban
                "amount" amount
                "immutable" true}]
    {"record" record "record_number" record-number
     "certificate" (unsigned-certificate "SafeguardingCertificate" record-number record-number)}))

(defn register-redemption
  "Validate + construct a REDEMPTION registration DRAFT -- paying out
  `amount` of e-money at PAR VALUE, on demand, per EMD2 Art. 11
  (redemption at par, at any time, on request). Pure function -- does not
  touch any real payout rail; it builds the RECORD an EMI operator would
  keep. Append-only: a wallet's redemption history is never overwritten,
  even across multiple partial redemptions."
  [wallet-id jurisdiction amount sequence]
  (when-not (and wallet-id (not= wallet-id ""))
    (throw (ex-info "redemption: wallet_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "redemption: jurisdiction required" {})))
  (when-not (number? amount)
    (throw (ex-info "redemption: amount required" {})))
  (when (<= amount 0)
    (throw (ex-info "redemption: amount must be > 0" {})))
  (when (< sequence 0)
    (throw (ex-info "redemption: sequence must be >= 0" {})))
  (let [record-number (str (str/upper-case jurisdiction) "-RDM-" (zero-pad sequence 8))
        record {"record_id" record-number
                "kind" "redemption-draft"
                "wallet_id" wallet-id
                "jurisdiction" jurisdiction
                "amount" amount
                "par_value" true
                "immutable" true}]
    {"record" record "record_number" record-number
     "certificate" (unsigned-certificate "RedemptionCertificate" record-number record-number)}))

(defn append
  "Append a registry record, returning a NEW list (never mutate history in place)."
  [history result]
  (conj (vec history) (get result "record")))
