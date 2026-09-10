(ns emi.governor-contract-test
  "The EMIGovernor contract, exercised end-to-end through the real
  `emi.operation` actor graph (not just unit calls to `emi.governor/check`)
  wherever the scenario needs the full intake -> advise -> govern ->
  decide -> commit|hold|approval path, plus a few direct `governor/check`
  calls for proposal shapes the mock advisor never emits on its own
  (fabricated interest/credit, effect mismatch) -- the same split
  `formation.governor-contract-test` / `banking.governor-contract-test`
  use."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [emi.facts :as facts]
            [emi.governor :as governor]
            [emi.store :as store]
            [emi.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :emi-operator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "op-1"}} {:thread-id tid :resume? true}))

;; ----------------------------- intake -----------------------------

(deftest clean-intake-auto-commits
  (let [db (store/seed-db)
        actor (op/build db)
        res (exec! actor "t1" {:op :wallet/intake :subject "w-1" :patch {:id "w-1"}})]
    (is (= :done (:status res)))
    (is (= :commit (get-in res [:state :disposition])))
    (is (= :intake (:status (store/wallet db "w-1"))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [db (store/seed-db)
          actor (op/build db)
          res (exec! actor "t2" {:op :jurisdiction/assess :subject "w-1"})]
      (is (= :interrupted (:status res)))
      (is (nil? (store/assessment-of db "w-1"))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [db (store/seed-db)
          actor (op/build db)
          res (exec! actor "t3" {:op :jurisdiction/assess :subject "w-2" :no-spec? true})]
      (is (= :done (:status res)))
      (is (= :hold (get-in res [:state :disposition]))))))

;; ----------------------------- kyc / sanctions -----------------------------

(deftest sanctions-hit-is-held-and-unoverridable
  (testing "a sanctions/PEP hit on a customer -> HOLD, and never reaches request-approval"
    (let [db (store/seed-db)
          actor (op/build db)
          res (exec! actor "t4" {:op :kyc/screen :subject "c-2"})]
      (is (= :done (:status res)))
      (is (= :hold (get-in res [:state :disposition])))
      (is (nil? (store/kyc-of db "c-2"))))))

;; ----------------------------- issuance -----------------------------

(deftest issuance-without-assessment-is-held
  (testing "emoney/issue before any jurisdiction assessment -> HOLD (evidence-incomplete)"
    (let [db (store/seed-db)
          actor (op/build db)
          _ (store/commit-record! db {:effect :kyc/set :path ["c-1"] :payload {:customer-id "c-1" :verdict :clear}})
          res (exec! actor "t5" {:op :emoney/issue :subject "w-1" :amount 1000})]
      (is (= :done (:status res)))
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (map :rule (get-in res [:state :verdict :violations])))))))

(deftest issuance-without-any-kyc-screening-is-held
  (testing "assessment is clean, but the customer was NEVER screened -> HOLD (kyc-incomplete), not a silent pass"
    (let [db (store/seed-db)
          actor (op/build db)]
      (store/commit-record! db {:effect :assessment/set :path ["w-1"]
                                :payload {:jurisdiction "JPN" :checklist (facts/evidence-checklist "JPN")}})
      (let [res (exec! actor "t6" {:op :emoney/issue :subject "w-1" :amount 1000})]
        (is (= :done (:status res)))
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:kyc-incomplete} (map :rule (get-in res [:state :verdict :violations]))))))))

(deftest issuance-with-an-incomplete-kyc-verdict-is-held
  (testing "a customer screened but only to :incomplete (missing id-doc) is not the same as :clear -> HOLD"
    (let [db (store/seed-db)
          actor (op/build db)]
      (store/commit-record! db {:effect :assessment/set :path ["w-1"]
                                :payload {:jurisdiction "JPN" :checklist (facts/evidence-checklist "JPN")}})
      (store/commit-record! db {:effect :kyc/set :path ["c-1"] :payload {:customer-id "c-1" :verdict :incomplete}})
      (let [res (exec! actor "t7" {:op :emoney/issue :subject "w-1" :amount 1000})]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:kyc-incomplete} (map :rule (get-in res [:state :verdict :violations]))))))))

(deftest issuance-always-escalates-then-human-decides
  (testing "a clean, fully-assessed issuance still ALWAYS interrupts for human approval -- actuation is never auto"
    (let [db (store/seed-db)
          actor (op/build db)]
      (store/commit-record! db {:effect :assessment/set :path ["w-1"]
                                :payload {:jurisdiction "JPN" :checklist (facts/evidence-checklist "JPN")}})
      (store/commit-record! db {:effect :kyc/set :path ["c-1"] :payload {:customer-id "c-1" :verdict :clear}})
      (let [res (exec! actor "t8" {:op :emoney/issue :subject "w-1" :amount 1000})]
        (is (= :interrupted (:status res)))
        (testing "approve -> commit, wallet credited"
          (let [res2 (approve! actor "t8")]
            (is (= :done (:status res2)))
            (is (= :commit (get-in res2 [:state :disposition])))
            (is (= 1000 (:balance (store/wallet db "w-1"))))
            (is (= :active (:status (store/wallet db "w-1")))))))))
  (testing "reject -> hold, nothing issued"
    (let [db (store/seed-db)
          actor (op/build db)]
      (store/commit-record! db {:effect :assessment/set :path ["w-1"]
                                :payload {:jurisdiction "JPN" :checklist (facts/evidence-checklist "JPN")}})
      (store/commit-record! db {:effect :kyc/set :path ["c-1"] :payload {:customer-id "c-1" :verdict :clear}})
      (exec! actor "t9" {:op :emoney/issue :subject "w-1" :amount 1000})
      (let [res (reject! actor "t9")]
        (is (= :hold (get-in res [:state :disposition])))
        (is (= 0 (:balance (store/wallet db "w-1"))))
        (is (empty? (store/emoney-history db)))))))

;; ----------------------------- EMD2 no-interest / no-credit -----------------------------

(deftest interest-rate-fabrication-is-held-unconditionally
  (testing "a proposal carrying a non-zero :interest-rate is a HARD violation regardless of op or confidence"
    (let [db (store/seed-db)
          req {:op :emoney/issue :subject "w-1"}
          proposal {:summary "発行、年利0.1%付与" :rationale "顧客誘因"
                    :cites ["資金決済法"] :effect :emoney/issued
                    :value {:amount 1000 :interest-rate 0.001}
                    :stake :actuation :confidence 0.95}
          v (governor/check req operator proposal db)]
      (is (:hard? v))
      (is (some #{:interest-or-credit-forbidden} (map :rule (:violations v)))))))

(deftest credit-extended-fabrication-is-held
  (testing "a proposal carrying a non-zero :credit-extended is a HARD violation -- an EMI must never lend against float"
    (let [db (store/seed-db)
          req {:op :emoney/issue :subject "w-1"}
          proposal {:summary "発行、与信枠付与" :rationale "顧客誘因"
                    :cites ["資金決済法"] :effect :emoney/issued
                    :value {:amount 1000 :credit-extended 500}
                    :stake :actuation :confidence 0.95}
          v (governor/check req operator proposal db)]
      (is (:hard? v))
      (is (some #{:interest-or-credit-forbidden} (map :rule (:violations v)))))))

(deftest zero-interest-rate-is-not-a-violation
  (testing "an explicit :interest-rate 0 is not itself forbidden -- only a NON-zero value is"
    (let [db (store/seed-db)
          req {:op :emoney/issue :subject "w-1"}
          proposal {:summary "発行" :rationale "評価用" :cites ["資金決済法"] :effect :emoney/issued
                    :value {:amount 1000 :interest-rate 0} :stake :actuation :confidence 0.9}
          v (governor/check req operator proposal db)]
      (is (not (some #{:interest-or-credit-forbidden} (map :rule (:violations v))))))))

;; ----------------------------- effect mismatch -----------------------------

(deftest effect-mismatch-is-hard
  (testing "an assess request whose proposal declares :effect :emoney/issued is rejected outright"
    (let [db (store/seed-db)
          req {:op :jurisdiction/assess :subject "w-1"}
          proposal {:summary "s" :rationale "r" :cites ["資金決済法"] :effect :emoney/issued
                    :value {} :stake nil :confidence 0.9}
          v (governor/check req operator proposal db)]
      (is (:hard? v))
      (is (some #{:effect-mismatch} (map :rule (:violations v)))))))

;; ----------------------------- post-closure intake -----------------------------

(deftest post-closure-intake-is-blocked
  (testing ":wallet/intake auto-commits with NO approval -- once :closed, it must not be able to smuggle changes in"
    (let [db (store/seed-db)
          actor (op/build db)]
      (store/with-wallets db {"w-1" (assoc (store/wallet db "w-1") :status :closed :balance 0)})
      (let [res (exec! actor "t10" {:op :wallet/intake :subject "w-1" :patch {:id "w-1" :status :closed}})]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:post-closure-intake-blocked} (map :rule (get-in res [:state :verdict :violations]))))))))

(deftest intake-before-closure-still-works-normally
  (testing "the block is post-closure ONLY -- pre-closure intake is unaffected and still auto-commits"
    (let [db (store/seed-db)
          actor (op/build db)
          res (exec! actor "t11" {:op :wallet/intake :subject "w-1" :patch {:id "w-1"}})]
      (is (= :commit (get-in res [:state :disposition]))))))

;; ----------------------------- intake fabrication -----------------------------

(deftest intake-cannot-fabricate-a-balance-or-active-status
  (testing "the ONE op that auto-commits with ZERO human approval must not be a backdoor for fake e-money"
    (let [db (store/seed-db)
          req {:op :wallet/intake :subject "w-1"}
          proposal {:summary "s" :rationale "r" :cites [:id] :effect :wallet/upsert
                    :value {:id "w-1" :balance 999999 :status :active} :stake nil :confidence 0.97}
          v (governor/check req operator proposal db)]
      (is (:hard? v))
      (is (some #{:intake-forbidden-field} (map :rule (:violations v))))
      (is (some #{:intake-forbidden-status} (map :rule (:violations v)))))))

(deftest intake-cannot-target-a-different-wallet-than-its-declared-subject
  (testing "a request declaring subject w-2 whose patch :id names w-1 lets a decoy pass while the real target is rewritten -- HARD blocked"
    (let [db (store/seed-db)
          req {:op :wallet/intake :subject "w-2"}
          proposal {:summary "s" :rationale "r" :cites [:id] :effect :wallet/upsert
                    :value {:id "w-1" :currency "USD"} :stake nil :confidence 0.97}
          v (governor/check req operator proposal db)]
      (is (:hard? v))
      (is (some #{:intake-subject-mismatch} (map :rule (:violations v)))))))

;; ----------------------------- safeguarding -----------------------------

(deftest safeguarding-requires-a-spec-basis
  (testing "a safeguarding confirmation for a jurisdiction with no spec-basis is HARD-held"
    (let [db (store/seed-db)
          req {:op :safeguarding/segregate :subject "w-2"}
          proposal {:summary "s" :rationale "r" :cites [] :effect :safeguarding/segregated
                    :value {:iban "DE89370400440532013000" :amount 100} :stake :actuation :confidence 0.9}
          v (governor/check req operator proposal db)]
      (is (:hard? v))
      (is (some #{:no-spec-basis} (map :rule (:violations v)))))))

(deftest safeguarding-with-an-invalid-iban-is-held
  (testing "INDEPENDENTLY recomputed IBAN checksum failure is HARD and un-overridable"
    (let [db (store/seed-db)
          req {:op :safeguarding/segregate :subject "w-1"}
          proposal {:summary "s" :rationale "r" :cites ["資金決済法"] :effect :safeguarding/segregated
                    :value {:iban "GB00NWBK00000000000000" :amount 100} :stake :actuation :confidence 0.9}
          v (governor/check req operator proposal db)]
      (is (:hard? v))
      (is (some #{:iban-checksum-invalid} (map :rule (:violations v)))))))

(deftest safeguarding-always-escalates-then-human-decides
  (testing "a clean safeguarding confirmation still ALWAYS interrupts -- actuation is never auto"
    (let [db (store/seed-db)
          actor (op/build db)
          res (exec! actor "t12" {:op :safeguarding/segregate :subject "w-1"
                                  :iban "DE89370400440532013000" :amount 100})]
      (is (= :interrupted (:status res)))
      (let [res2 (approve! actor "t12")]
        (is (= :commit (get-in res2 [:state :disposition])))
        (is (= "DE89370400440532013000" (:safeguarding-iban (store/wallet db "w-1"))))))))

;; ----------------------------- redemption -----------------------------

(deftest redemption-requires-a-spec-basis
  (testing "a redemption for a jurisdiction with no spec-basis is HARD-held"
    (let [db (store/seed-db)
          req {:op :redemption/redeem :subject "w-2"}
          proposal {:summary "s" :rationale "r" :cites [] :effect :redemption/redeemed
                    :value {:amount 0} :stake :actuation :confidence 0.9}
          v (governor/check req operator proposal db)]
      (is (:hard? v))
      (is (some #{:no-spec-basis} (map :rule (:violations v)))))))

(deftest redemption-exceeding-balance-is-held
  (testing "INDEPENDENTLY recomputed balance check rejects an oversized redemption -- HARD, un-overridable"
    (let [db (store/seed-db)
          req {:op :redemption/redeem :subject "w-1"}
          proposal {:summary "s" :rationale "r" :cites ["資金決済法"] :effect :redemption/redeemed
                    :value {:amount 999999} :stake :actuation :confidence 0.9}
          v (governor/check req operator proposal db)]
      (is (:hard? v))
      (is (some #{:insufficient-balance} (map :rule (:violations v)))))))

(deftest redemption-always-escalates-then-human-decides
  (testing "a clean redemption still ALWAYS interrupts -- actuation is never auto"
    (let [db (store/seed-db)
          actor (op/build db)]
      (store/commit-record! db {:effect :kyc/set :path ["c-1"] :payload {:customer-id "c-1" :verdict :clear}})
      (store/commit-record! db {:effect :emoney/issued :path ["w-1"] :value {:amount 1000}})
      (let [res (exec! actor "t13" {:op :redemption/redeem :subject "w-1" :amount 400})]
        (is (= :interrupted (:status res)))
        (let [res2 (approve! actor "t13")]
          (is (= :commit (get-in res2 [:state :disposition])))
          (is (= 600 (:balance (store/wallet db "w-1"))))
          (is (= :active (:status (store/wallet db "w-1")))))))))

(deftest full-redemption-closes-the-wallet-and-double-touch-is-held
  (testing "redeeming the full balance closes the wallet; any further issuance or redemption is HARD-held"
    (let [db (store/seed-db)
          actor (op/build db)]
      (store/commit-record! db {:effect :kyc/set :path ["c-1"] :payload {:customer-id "c-1" :verdict :clear}})
      (store/commit-record! db {:effect :emoney/issued :path ["w-1"] :value {:amount 1000}})
      (exec! actor "t14" {:op :redemption/redeem :subject "w-1" :amount 1000})
      (approve! actor "t14")
      (is (= :closed (:status (store/wallet db "w-1"))))
      (let [res (exec! actor "t15" {:op :redemption/redeem :subject "w-1" :amount 1})]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:wallet-already-closed} (map :rule (get-in res [:state :verdict :violations])))))
      (let [res (exec! actor "t16" {:op :emoney/issue :subject "w-1" :amount 1})]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:wallet-already-closed} (map :rule (get-in res [:state :verdict :violations]))))))))

;; ----------------------------- audit ledger -----------------------------

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [db (store/seed-db)
          actor (op/build db)]
      (exec! actor "l1" {:op :wallet/intake :subject "w-1" :patch {:id "w-1"}})
      (exec! actor "l2" {:op :kyc/screen :subject "c-2"})
      (is (= 2 (count (store/ledger db)))))))

(deftest auto-committed-ledger-fact-has-no-fabricated-approver
  (testing "an auto-committed op (phase-3 wallet/intake) records :approved-by nil, not a fabricated approver"
    (let [db (store/seed-db)
          actor (op/build db)]
      (exec! actor "l3" {:op :wallet/intake :subject "w-1" :patch {:id "w-1"}})
      (is (nil? (:approved-by (last (store/ledger db))))))))

(deftest committed-ledger-fact-records-the-actual-approver
  (testing "a human-approved issuance's ledger fact records WHO approved it"
    (let [db (store/seed-db)
          actor (op/build db)]
      (store/commit-record! db {:effect :assessment/set :path ["w-1"]
                                :payload {:jurisdiction "JPN" :checklist (facts/evidence-checklist "JPN")}})
      (store/commit-record! db {:effect :kyc/set :path ["c-1"] :payload {:customer-id "c-1" :verdict :clear}})
      (exec! actor "l4" {:op :emoney/issue :subject "w-1" :amount 1000})
      (approve! actor "l4")
      (is (= "op-1" (:approved-by (last (store/ledger db))))))))
