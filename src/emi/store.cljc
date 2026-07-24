(ns emi.store
  "SSoT for the EMI (Electronic Money Institution) actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam every
  sibling cloud-itonami actor uses (`formation.store` / `banking.store`
  etc.).

  MemStore-only at this maturity stage (R0, ADR-2607247000): an atom of
  EDN, the deterministic default for dev/tests/demo, with no external
  deps. A Datomic/kotoba-server-backed store is the next seam to add
  (follow `formation.store`'s `DatomicStore` pattern in
  `cloud-itonami-isic-6910` when this actor is ready to grow past R0) --
  every caller of this namespace already goes through the `Store`
  protocol, so that swap stays a configuration change, not a rewrite.

  The ledger stays append-only: 'who issued/safeguarded/redeemed what,
  for which wallet, on what jurisdictional basis, approved by whom' is
  always a query over an immutable log -- the audit trail a customer
  trusting an EMI with their e-money balance needs, and the evidence an
  operator needs if an issuance or redemption is later disputed."
  (:require [emi.registry :as registry]))

(defprotocol Store
  (wallet [s id])
  (all-wallets [s])
  (customer [s id])
  (kyc-of [s customer-id] "committed KYC/CDD screening verdict for a customer, or nil")
  (assessment-of [s wallet-id] "committed jurisdiction assessment (evidence checklist), or nil")
  (ledger [s])
  (emoney-history [s] "the append-only e-money record history (emi.registry drafts: issuance/safeguarding/redemption)")
  (next-sequence [s jurisdiction] "next e-money-record sequence for a jurisdiction")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-wallets [s wallets] "replace/seed the wallet directory (map id->wallet)")
  (with-customers [s customers] "replace/seed the customer directory (map id->customer)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained customer/wallet set so the actor + tests run
  offline."
  []
  {:wallets
   {"w-1" {:id "w-1" :customer-id "c-1" :jurisdiction "JPN" :currency "JPY"
           :balance 0 :status :intake}
    "w-2" {:id "w-2" :customer-id "c-2" :jurisdiction "ATL" :currency "USD"
           :balance 0 :status :intake}}
   :customers
   {"c-1" {:id "c-1" :name "田中 一郎" :sanctions-hit? false :id-doc "passport-jp-****1234"}
    "c-2" {:id "c-2" :name "J. Doe" :sanctions-hit? true :id-doc nil}
    ;; not on any wallet by default -- a spare customer for tests that
    ;; need a :sanctions-hit? false / :id-doc nil customer (screens to
    ;; :incomplete, never :clear, without also tripping :sanctions-hit).
    "c-3" {:id "c-3" :name "鈴木 花子" :sanctions-hit? false :id-doc nil}}})

;; ----------------------------- shared actuation logic -----------------------------

(defn- issue!
  "Backend-agnostic `:emoney/issued` -- looks up the wallet via the
  protocol, drafts the issuance record, and returns {:result ..
  :wallet-patch ..} for the caller to persist. Pure w.r.t. any particular
  backend's transaction mechanics."
  [s wallet-id amount]
  (let [w (wallet s wallet-id)
        seq-n (next-sequence s (:jurisdiction w))
        result (registry/register-issuance wallet-id (:jurisdiction w) amount (:currency w) seq-n)]
    {:result result
     :wallet-patch {:status :active
                    :balance (+ (:balance w 0) amount)
                    :emoney-record-id (get result "record_number")}}))

(defn- safeguard!
  "Backend-agnostic `:safeguarding/segregated` -- looks up the wallet via
  the protocol, drafts the safeguarding record, and returns {:result ..
  :wallet-patch ..} for the caller to persist. The safeguarding record is
  APPENDED to emoney-history; a wallet's prior safeguarding confirmations
  are never overwritten (append-only, matching every sibling actor's
  registry-history discipline)."
  [s wallet-id iban amount]
  (let [w (wallet s wallet-id)
        seq-n (next-sequence s (:jurisdiction w))
        result (registry/register-safeguarding wallet-id (:jurisdiction w) iban amount seq-n)]
    {:result result
     :wallet-patch {:safeguarding-iban iban
                    :safeguarded-amount (+ (:safeguarded-amount w 0) amount)}}))

(defn- redeem!
  "Backend-agnostic `:redemption/redeemed` -- looks up the wallet via the
  protocol, drafts the redemption record, and returns {:result ..
  :wallet-patch ..} for the caller to persist. Debits the wallet balance
  at par; the wallet closes (`:status :closed`) only when the redemption
  drives the balance to exactly zero, otherwise it stays `:active` (EMD2
  Art. 11 allows redemption in whole OR in part)."
  [s wallet-id amount]
  (let [w (wallet s wallet-id)
        seq-n (next-sequence s (:jurisdiction w))
        result (registry/register-redemption wallet-id (:jurisdiction w) amount seq-n)
        remaining (- (:balance w 0) amount)]
    {:result result
     :wallet-patch {:balance remaining
                    :status (if (zero? remaining) :closed :active)}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (wallet [_ id] (get-in @a [:wallets id]))
  (all-wallets [_] (sort-by :id (vals (:wallets @a))))
  (customer [_ id] (get-in @a [:customers id]))
  (kyc-of [_ id] (get-in @a [:kyc id]))
  (assessment-of [_ wallet-id] (get-in @a [:assessments wallet-id]))
  (ledger [_] (:ledger @a))
  (emoney-history [_] (:emoney @a))
  (next-sequence [_ jurisdiction]
    (get-in @a [:sequences jurisdiction] 0))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :wallet/upsert
      (swap! a update-in [:wallets (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :kyc/set
      (swap! a assoc-in [:kyc (first path)] payload)

      :emoney/issued
      (let [wallet-id (first path)
            amount (:amount value)
            {:keys [result wallet-patch]} (issue! s wallet-id amount)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sequences (:jurisdiction (get-in state [:wallets wallet-id]))] (fnil inc 0))
                       (update-in [:wallets wallet-id] merge wallet-patch)
                       (update :emoney registry/append result))))
        result)

      :safeguarding/segregated
      (let [wallet-id (first path)
            {:keys [iban amount]} value
            {:keys [result wallet-patch]} (safeguard! s wallet-id iban amount)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sequences (:jurisdiction (get-in state [:wallets wallet-id]))] (fnil inc 0))
                       (update-in [:wallets wallet-id] merge wallet-patch)
                       (update :emoney registry/append result))))
        result)

      :redemption/redeemed
      (let [wallet-id (first path)
            amount (:amount value)
            {:keys [result wallet-patch]} (redeem! s wallet-id amount)]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sequences (:jurisdiction (get-in state [:wallets wallet-id]))] (fnil inc 0))
                       (update-in [:wallets wallet-id] merge wallet-patch)
                       (update :emoney registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-wallets [s wallets] (when (seq wallets) (swap! a assoc :wallets wallets)) s)
  (with-customers [s customers] (when (seq customers) (swap! a assoc :customers customers)) s))

(defn seed-db
  "A MemStore seeded with the demo customer/wallet set. The deterministic default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :kyc {} :ledger [] :sequences {} :emoney []))))

(defn empty-db
  "A MemStore with no wallets/customers seeded -- for tests that want to
  build their own scenario from scratch via `with-wallets`/`with-customers`."
  []
  (->MemStore (atom {:wallets {} :customers {} :assessments {} :kyc {}
                     :ledger [] :sequences {} :emoney []})))
