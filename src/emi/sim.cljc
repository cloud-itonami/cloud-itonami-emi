(ns emi.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean wallet through intake
  -> jurisdiction assessment -> KYC/CDD screening -> e-money issuance
  (always escalates) -> human approval -> commit -> a safeguarding-account
  confirmation -> a redemption (both also always escalate), then shows
  five HARD holds (a sanctions hit, a fabricated jurisdiction, an
  interest-on-e-money fabrication attempt, an oversized redemption, and a
  double-touch of an already-closed wallet) that never reach a human at
  all, and prints the audit ledger + the draft e-money record history."
  (:require [langgraph.graph :as g]
            [emi.store :as store]
            [emi.operation :as op]
            [emi.emiadvisor :as emiadvisor]))

(def operator {:actor-id "op-1" :actor-role :emi-operator :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== intake w-1 (JPN, clean customer) ==")
    (println (exec! actor "t1" {:op :wallet/intake :subject "w-1"
                                :patch {:id "w-1"}} operator))

    (println "== jurisdiction/assess w-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :jurisdiction/assess :subject "w-1"} operator))
    (println (approve! actor "t2"))

    (println "== kyc/screen c-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :kyc/screen :subject "c-1"} operator))
    (println (approve! actor "t3"))

    (println "== emoney/issue w-1: 100000 JPY (always escalates -- actuation) ==")
    (let [r (exec! actor "t4" {:op :emoney/issue :subject "w-1" :amount 100000} operator)]
      (println r)
      (println "-- human operator approves --")
      (println (approve! actor "t4")))

    (println "== safeguarding/segregate w-1: confirm 100000 JPY held in DE89370400440532013000 (valid ISO 7064 MOD 97-10 checksum; always escalates -- actuation) ==")
    (let [r (exec! actor "t4b" {:op :safeguarding/segregate :subject "w-1"
                               :iban "DE89370400440532013000" :amount 100000} operator)]
      (println r)
      (println "-- human operator approves --")
      (println (approve! actor "t4b")))

    (println "== redemption/redeem w-1: 100000 JPY, at par, closes the wallet (always escalates -- actuation) ==")
    (let [r (exec! actor "t4c" {:op :redemption/redeem :subject "w-1" :amount 100000} operator)]
      (println r)
      (println "-- human operator approves --")
      (println (approve! actor "t4c")))

    (println "== redemption/redeem w-1 AGAIN (already closed -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t4d" {:op :redemption/redeem :subject "w-1" :amount 1} operator))

    (println "== kyc/screen c-2 (sanctions hit -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t5" {:op :kyc/screen :subject "c-2"} operator))

    (println "== jurisdiction/assess w-2 (ATL, no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :jurisdiction/assess :subject "w-2" :no-spec? true} operator))

    (println "== safeguarding/segregate w-1 with an INVALID IBAN (fails its own ISO 7064 MOD 97-10 checksum -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t6b" {:op :safeguarding/segregate :subject "w-1"
                                 :iban "JP12JPBK0000000001234567" :amount 1} operator))

    (println "== emoney/issue w-2 attempting to smuggle a non-zero :interest-rate -> HARD hold, EMD2 forbids interest on e-money ==")
    (let [malicious-advisor (reify emiadvisor/Advisor
                              (-advise [_ _st _req]
                                {:summary "w-2 へ発行、ついでに年利0.5%を付与"
                                 :rationale "顧客誘因のため"
                                 :cites ["fabricated"]
                                 :effect :emoney/issued
                                 :value {:amount 1000 :interest-rate 0.005}
                                 :stake :actuation
                                 :confidence 0.95}))
          bad-actor (op/build db {:advisor malicious-advisor})]
      (println (exec! bad-actor "t7" {:op :emoney/issue :subject "w-2" :amount 1000} operator)))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft e-money records ==")
    (doseq [r (store/emoney-history db)] (println r))))
