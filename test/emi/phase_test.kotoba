(ns emi.phase-test
  "The phase table as executable tests. The single invariant this repo
  cannot regress on: `:emoney/issue`, `:safeguarding/segregate` and
  `:redemption/redeem` must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [emi.phase :as phase]))

(def actuation-ops
  "Every op that touches a real e-money movement. This set is the single
  source of truth for `actuation-never-auto-at-any-phase` below -- add a
  new actuation op here, not just in emi.phase, so a forgotten :auto
  exclusion fails loudly instead of silently."
  #{:emoney/issue :safeguarding/segregate :redemption/redeem})

(deftest actuation-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in future entries, auto-commits a real actuation op"
    (doseq [[n {:keys [auto]}] phase/phases
            op actuation-ops]
      (is (not (contains? auto op))
          (str "phase " n " must not auto-commit " op)))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-intake
  (is (= #{:wallet/intake} (:auto (get phase/phases 3)))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :wallet/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :emoney/issue} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :wallet/intake} :commit)))))

(deftest reads-pass-through-unchanged-regardless-of-phase
  (is (= :commit (:disposition (phase/gate 0 {:op :coverage/report} :commit))))
  (is (= :hold (:disposition (phase/gate 0 {:op :coverage/report} :hold)))))
