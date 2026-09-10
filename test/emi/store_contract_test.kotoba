(ns emi.store-contract-test
  "The Store contract, run against `MemStore` -- the deterministic default
  at this maturity stage (R0, ADR-2607247000; see `emi.store`'s
  docstring for the DatomicStore seam this contract is designed to grow
  into, following `formation.store-contract-test`'s pattern)."
  (:require [clojure.test :refer [deftest is testing]]
            [emi.store :as store]))

(deftest read-parity
  (let [s (store/seed-db)]
    (is (= "JPN" (:jurisdiction (store/wallet s "w-1"))))
    (is (= "JPY" (:currency (store/wallet s "w-1"))))
    (is (= 0 (:balance (store/wallet s "w-1"))))
    (is (= :intake (:status (store/wallet s "w-1"))))
    (is (= "田中 一郎" (:name (store/customer s "c-1"))))
    (is (false? (:sanctions-hit? (store/customer s "c-1"))))
    (is (true? (:sanctions-hit? (store/customer s "c-2"))))
    (is (= ["w-1" "w-2"] (mapv :id (store/all-wallets s))))
    (is (nil? (store/kyc-of s "c-1")))
    (is (nil? (store/assessment-of s "w-1")))
    (is (= [] (store/ledger s)))
    (is (= [] (store/emoney-history s)))
    (is (zero? (store/next-sequence s "JPN")))))

(deftest write-and-ledger-parity
  (let [s (store/seed-db)]
    (testing "partial wallet upsert merges, preserving untouched fields"
      (store/commit-record! s {:effect :wallet/upsert :value {:id "w-1" :status :intake}})
      (is (= :intake (:status (store/wallet s "w-1"))))
      (is (= "JPY" (:currency (store/wallet s "w-1"))) "currency preserved"))
    (testing "assessment / kyc payloads commit and read back"
      (store/commit-record! s {:effect :assessment/set :path ["w-1"]
                               :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
      (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "w-1")))
      (store/commit-record! s {:effect :kyc/set :path ["c-1"]
                               :payload {:customer-id "c-1" :verdict :clear}})
      (is (= {:customer-id "c-1" :verdict :clear} (store/kyc-of s "c-1"))))
    (testing "issuance drafts an e-money record, credits the wallet, and advances the sequence"
      (store/commit-record! s {:effect :emoney/issued :path ["w-1"] :value {:amount 1000}})
      (is (= "JPN-EMI-00000000" (get (first (store/emoney-history s)) "record_id")))
      (is (= "issuance-draft" (get (first (store/emoney-history s)) "kind")))
      (is (= 1000 (:balance (store/wallet s "w-1"))))
      (is (= :active (:status (store/wallet s "w-1"))))
      (is (= 1 (count (store/emoney-history s))))
      (is (= 1 (store/next-sequence s "JPN"))))
    (testing "safeguarding drafts a record and accumulates the safeguarded amount"
      (store/commit-record! s {:effect :safeguarding/segregated :path ["w-1"]
                               :value {:iban "DE89370400440532013000" :amount 1000}})
      (is (= "DE89370400440532013000" (:safeguarding-iban (store/wallet s "w-1"))))
      (is (= 1000 (:safeguarded-amount (store/wallet s "w-1"))))
      (is (= 2 (count (store/emoney-history s)))))
    (testing "redemption debits the wallet at par and closes it when the balance hits zero"
      (store/commit-record! s {:effect :redemption/redeemed :path ["w-1"] :value {:amount 1000}})
      (is (= 0 (:balance (store/wallet s "w-1"))))
      (is (= :closed (:status (store/wallet s "w-1"))))
      (is (= 3 (count (store/emoney-history s)))))
    (testing "ledger is append-only and order-preserving"
      (store/append-ledger! s {:op :a :disposition :commit})
      (store/append-ledger! s {:op :b :disposition :hold})
      (is (= [:commit :hold] (mapv :disposition (store/ledger s)))))))

(deftest partial-redemption-keeps-the-wallet-active
  (let [s (store/seed-db)]
    (store/commit-record! s {:effect :emoney/issued :path ["w-1"] :value {:amount 1000}})
    (store/commit-record! s {:effect :redemption/redeemed :path ["w-1"] :value {:amount 400}})
    (is (= 600 (:balance (store/wallet s "w-1"))))
    (is (= :active (:status (store/wallet s "w-1"))) "partial redemption does not close the wallet")))

(deftest empty-store-is-usable
  (let [s (store/empty-db)]
    (is (nil? (store/wallet s "nope")))
    (is (= [] (store/all-wallets s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/emoney-history s)))
    (is (zero? (store/next-sequence s "JPN")))
    (store/with-wallets s {"x" {:id "x" :customer-id "c-x" :jurisdiction "JPN"
                                :currency "JPY" :balance 0 :status :intake}})
    (is (= "JPY" (:currency (store/wallet s "x"))))))
