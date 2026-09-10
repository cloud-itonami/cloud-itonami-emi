(ns emi.llm-advisor-test
  "The real-inference advisor (langchain.model ChatModel), driven offline by
  langchain's mock-model. Proves: a real LLM proposal is parsed, still
  fully censored by the EMIGovernor, and that an unparseable/garbage
  response -- or one that fabricates a jurisdiction's requirements, or one
  that answers a harmless-looking request with a mismatched, higher-stakes
  :effect, or one that tries to smuggle interest onto an e-money balance
  -- can never auto-issue, auto-safeguard or auto-redeem."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [langchain.model :as model]
            [emi.emiadvisor :as emiadvisor]
            [emi.governor :as governor]
            [emi.store :as store]
            [emi.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :emi-operator :phase 3})
(def assess-req {:op :jurisdiction/assess :subject "w-1"})

(defn- advise-with [req content]
  (emiadvisor/-advise (emiadvisor/llm-advisor (model/mock-model [{:role :assistant :content content}]))
                      (store/seed-db) req))

(deftest clean-llm-assessment-is-parsed-and-accepted
  (let [p (advise-with assess-req
                       (str "{:summary \"JPN 向け必要書類を提案\" :rationale \"金融庁の公式ソースに基づく\" "
                            ":cites [\"資金決済法\" \"https://www.fsa.go.jp/policy/kessai/index.html\"] "
                            ":effect :assessment/set "
                            ":value {:jurisdiction \"JPN\" :checklist [] :spec-basis \"https://www.fsa.go.jp/policy/kessai/index.html\"} "
                            ":stake nil :confidence 0.9}"))]
    (is (= :assessment/set (:effect p)))
    (is (seq (:cites p)))
    (is (= 0.9 (:confidence p)))
    (testing "the governor accepts a proposal that actually cites a spec-basis"
      (is (:ok? (governor/check assess-req operator p (store/seed-db)))))))

(deftest llm-fabricating-a-jurisdiction-is-rejected
  (testing "even a confident LLM can't invent a jurisdiction's e-money-issuance requirements -- spec-basis gate holds"
    (let [p (advise-with assess-req
                         (str "{:summary \"ATL 向け必要書類を提案\" :rationale \"一般的な慣行に基づく推測\" "
                              ":cites [] :effect :assessment/set "
                              ":value {:jurisdiction \"ATL\" :checklist [\"some doc\"]} "
                              ":confidence 0.95}"))
          v (governor/check assess-req operator p (store/seed-db))]
      (is (:hard? v))
      (is (some #{:no-spec-basis} (map :rule (:violations v)))))))

(deftest llm-declaring-a-sanctions-hit-is-unoverridable
  (testing "an LLM-reported sanctions hit still forces HOLD, regardless of confidence"
    (let [p (advise-with {:op :kyc/screen :subject "c-1"}
                         (str "{:summary \"制裁リスト一致\" :rationale \"screening provider hit\" "
                              ":cites [:sanctions-list] :effect :kyc/set "
                              ":value {:customer-id \"c-1\" :verdict :hit} :confidence 0.98}"))
          v (governor/check {:op :kyc/screen :subject "c-1"} operator p (store/seed-db))]
      (is (:hard? v))
      (is (some #{:sanctions-hit} (map :rule (:violations v)))))))

(deftest llm-smuggling-interest-onto-an-emoney-balance-is-unoverridable
  (testing "an LLM proposing a non-zero :interest-rate on an issuance is a HARD violation regardless of confidence or spec-basis"
    (let [req {:op :emoney/issue :subject "w-1" :amount 1000}
          p (advise-with req
                         (str "{:summary \"発行、年利0.3%付与\" :rationale \"顧客誘因\" "
                              ":cites [\"資金決済法\"] :effect :emoney/issued "
                              ":value {:amount 1000 :interest-rate 0.003} :stake :actuation :confidence 0.95}"))
          v (governor/check req operator p (store/seed-db))]
      (is (:hard? v))
      (is (some #{:interest-or-credit-forbidden} (map :rule (:violations v)))))))

(deftest unparseable-llm-output-never-auto-commits
  (testing "garbage / refusal -> safe noop at confidence 0 -> governor won't pass it"
    (let [p (advise-with assess-req "申し訳ございませんが、その法域についてはお答えできません。")]
      (is (= :noop (:effect p)))
      (is (= 0.0 (:confidence p)))
      (is (not (:ok? (governor/check assess-req operator p (store/seed-db))))))))

(deftest llm-answering-an-assessment-request-with-an-issuance-effect-is-rejected
  (testing "a harmless-looking :jurisdiction/assess request answered with :effect
            :emoney/issued -- even with plausible cites and high confidence --
            is a HARD violation, not just a low-confidence escalation"
    (let [p (advise-with assess-req
                         (str "{:summary \"JPN 向け必要書類を提案\" :rationale \"金融庁の公式ソースに基づく\" "
                              ":cites [\"資金決済法\" \"https://www.fsa.go.jp/policy/kessai/index.html\"] "
                              ":effect :emoney/issued "
                              ":value {:amount 1000} "
                              ":stake nil :confidence 0.95}"))
          v (governor/check assess-req operator p (store/seed-db))]
      (is (:hard? v))
      (is (some #{:effect-mismatch} (map :rule (:violations v)))))))

(deftest effect-mismatch-cannot-actually-issue-through-the-full-actor-graph
  (testing "end-to-end reproduction: a :jurisdiction/assess request whose LLM proposal
            declares :effect :emoney/issued must HOLD outright (no interrupt, no
            approval step at all) and leave the wallet completely untouched -- not
            merely fail a unit check."
    (let [db (store/seed-db)
          before (store/wallet db "w-1")
          advisor (emiadvisor/llm-advisor
                   (model/mock-model
                    [{:role :assistant
                      :content (str "{:summary \"JPN 向け必要書類を提案\" :rationale \"金融庁の公式ソースに基づく\" "
                                    ":cites [\"資金決済法\" \"https://www.fsa.go.jp/policy/kessai/index.html\"] "
                                    ":effect :emoney/issued "
                                    ":value {:amount 1000} "
                                    ":stake nil :confidence 0.95}")}]))
          actor (op/build db {:advisor advisor})
          res (g/run* actor {:request assess-req :context operator} {:thread-id "exploit"})]
      (is (= :done (:status res)) "settles immediately -- never even reaches request-approval")
      (is (= :hold (get-in res [:state :disposition])))
      (is (= before (store/wallet db "w-1")) "wallet completely unchanged")
      (is (empty? (store/emoney-history db)) "nothing issued")
      (is (nil? (store/assessment-of db "w-1")) "no assessment written either"))))
