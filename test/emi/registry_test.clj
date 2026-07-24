(ns emi.registry-test
  "Conformance tests for `emi.registry` -- the ISO 7064 MOD 97-10 IBAN
  checksum ported verbatim from `banking.registry`
  (cloud-itonami-isic-6419), plus the issuance/safeguarding/redemption
  record builders."
  (:require [clojure.test :refer [deftest is testing]]
            [emi.registry :as r]))

;; ----------------------------- IBAN checksum -----------------------------

(deftest known-valid-ibans-pass
  (testing "textbook example IBANs (widely published as valid) pass ISO 7064 MOD 97-10"
    (is (not (r/iban-checksum-invalid? {:iban "DE89370400440532013000"})))
    (is (not (r/iban-checksum-invalid? {:iban "GB29NWBK60161331926819"})))))

(deftest corrupted-iban-fails
  (testing "flipping one digit of a valid IBAN must fail the checksum"
    (let [good "DE89370400440532013000"
          bad  (str (subs good 0 (dec (count good))) (if (= \0 (last good)) \1 \0))]
      (is (not (r/iban-checksum-invalid? {:iban good})))
      (is (r/iban-checksum-invalid? {:iban bad})))))

(deftest nil-or-malformed-iban-is-invalid
  (is (r/iban-checksum-invalid? {:iban nil}))
  (is (r/iban-checksum-invalid? {:iban "TOOSHORT"}))
  (is (r/iban-checksum-invalid? {:iban "1234NOTANIBANATALL"})))

;; ----------------------------- issuance -----------------------------

(deftest certificate-is-a-draft-not-a-real-issuance
  (let [result (r/register-issuance "w-1" "JPN" 1000 "JPY" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= false (get-in result ["certificate" "issued_by_registry"])))
    (is (= "draft-unsigned" (get-in result ["certificate" "status"])))))

(deftest issuance-assigns-record-number-and-is-par-value
  (let [result (r/register-issuance "w-1" "JPN" 1000 "JPY" 7)]
    (is (= "JPN-EMI-00000007" (get result "record_number")))
    (is (= true (get-in result ["record" "par_value"])))
    (is (= true (get-in result ["record" "immutable"])))
    (is (= "issuance-draft" (get-in result ["record" "kind"])))))

(deftest issuance-validation-rules
  (is (thrown? Exception (r/register-issuance "" "JPN" 1000 "JPY" 0)))
  (is (thrown? Exception (r/register-issuance "w-1" "" 1000 "JPY" 0)))
  (is (thrown? Exception (r/register-issuance "w-1" "JPN" 1000 "" 0)))
  (is (thrown? Exception (r/register-issuance "w-1" "JPN" 0 "JPY" 0)))
  (is (thrown? Exception (r/register-issuance "w-1" "JPN" -1 "JPY" 0)))
  (is (thrown? Exception (r/register-issuance "w-1" "JPN" 1000 "JPY" -1))))

;; ----------------------------- safeguarding -----------------------------

(deftest safeguarding-record-carries-the-iban
  (let [result (r/register-safeguarding "w-1" "JPN" "DE89370400440532013000" 1000 0)]
    (is (= "JPN-SFG-00000000" (get result "record_number")))
    (is (= "DE89370400440532013000" (get-in result ["record" "iban"])))
    (is (= "safeguarding-draft" (get-in result ["record" "kind"])))))

(deftest safeguarding-validation-rules
  (is (thrown? Exception (r/register-safeguarding "" "JPN" "DE89370400440532013000" 1000 0)))
  (is (thrown? Exception (r/register-safeguarding "w-1" "JPN" "" 1000 0)))
  (is (thrown? Exception (r/register-safeguarding "w-1" "JPN" "DE89370400440532013000" 0 0))))

;; ----------------------------- redemption -----------------------------

(deftest redemption-record-is-par-value
  (let [result (r/register-redemption "w-1" "JPN" 500 0)]
    (is (= "JPN-RDM-00000000" (get result "record_number")))
    (is (= true (get-in result ["record" "par_value"])))
    (is (= "redemption-draft" (get-in result ["record" "kind"])))))

(deftest redemption-validation-rules
  (is (thrown? Exception (r/register-redemption "" "JPN" 500 0)))
  (is (thrown? Exception (r/register-redemption "w-1" "JPN" 0 0))))

;; ----------------------------- history append-only -----------------------------

(deftest history-is-append-only-across-kinds
  (let [iss (r/register-issuance "w-1" "JPN" 1000 "JPY" 0)
        hist (r/append [] iss)
        sfg (r/register-safeguarding "w-1" "JPN" "DE89370400440532013000" 1000 1)
        hist2 (r/append hist sfg)
        rdm (r/register-redemption "w-1" "JPN" 1000 2)
        hist3 (r/append hist2 rdm)]
    (is (= 3 (count hist3)))
    (is (= "issuance-draft" (get-in hist3 [0 "kind"])))
    (is (= "safeguarding-draft" (get-in hist3 [1 "kind"])))
    (is (= "redemption-draft" (get-in hist3 [2 "kind"])))))
