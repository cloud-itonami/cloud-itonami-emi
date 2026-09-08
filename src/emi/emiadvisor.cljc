(ns emi.emiadvisor
  "EMI-LLM client -- the *contained intelligence node*.

  It normalizes wallet intake, drafts a per-jurisdiction evidence
  checklist for e-money issuance authorization, screens customers against
  a KYC/CDD/sanctions signal, drafts the e-money-issuance action, the
  safeguarding-account-movement action, and the redemption action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a *proposal*
  (with a rationale + the fields it cited), never a committed record or a
  real movement of money. Every output is censored downstream by
  `emi.governor` before anything touches the SSoT, and `:emoney/issue` /
  `:safeguarding/segregate` / `:redemption/redeem` proposals NEVER
  auto-commit at any phase -- see README `Actuation`.

  Like `formation.registrarllm` / `banking.bankingadvisor`, this is a
  deterministic mock so the actor graph runs offline and the governor
  contract is exercised end-to-end. In production this calls a real LLM
  (kotoba-llm or equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation if it touches real money movement
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [kotoba.lang.text :as str]
            [emi.facts :as facts]
            [emi.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent a customer, jurisdiction or currency. High confidence,
  low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "ウォレットレコード更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :wallet/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction e-money-issuance evidence checklist draft. `:no-spec?`
  injects the failure mode we must defend against: proposing a checklist
  for a jurisdiction with NO official spec-basis in `emi.facts` -- the
  EMIGovernor must reject this (never invent a country's e-money law)."
  [db {:keys [subject no-spec?]}]
  (let [w (store/wallet db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction w))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "emi.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-kyc
  "KYC / CDD / sanctions screening draft. `:sanctions-hit?` on the customer
  record injects the failure mode: the EMIGovernor must HOLD,
  un-overridably, on any sanctions/PEP hit. Missing identification yields
  low confidence -> escalate rather than auto-clear."
  [db {:keys [subject]}]
  (let [c (store/customer db subject)]
    (cond
      (nil? c)
      {:summary "対象顧客が見つかりません" :rationale "no customer record"
       :cites [] :effect :kyc/set :value {:customer-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (:sanctions-hit? c)
      {:summary    (str (:name c) ": 制裁/PEPリストと一致")
       :rationale  "スクリーニングが一致を検出。人手確認とホールドが必須。"
       :cites      [:sanctions-list]
       :effect     :kyc/set
       :value      {:customer-id subject :verdict :hit}
       :stake      nil
       :confidence 0.95}

      (nil? (:id-doc c))
      {:summary    (str (:name c) ": 本人確認書類が未提出")
       :rationale  "本人確認書類が無いため確信度を上げられない。"
       :cites      [:id-doc]
       :effect     :kyc/set
       :value      {:customer-id subject :verdict :incomplete}
       :stake      nil
       :confidence 0.4}

      :else
      {:summary    (str (:name c) ": 制裁リスト非一致、本人確認書類あり")
       :rationale  "本人確認書類確認 + 制裁リスト非一致。"
       :cites      [:id-doc :sanctions-list]
       :effect     :kyc/set
       :value      {:customer-id subject :verdict :clear}
       :stake      nil
       :confidence 0.9})))

(defn- propose-issuance
  "Draft the actual e-money ISSUANCE action -- crediting the wallet at
  PAR VALUE against `amount` of received funds. ALWAYS `:stake
  :actuation` -- this is a REAL-WORLD act (a customer's e-money balance
  changes), never a draft the actor may auto-run. See README `Actuation`:
  no phase ever adds this op to a phase's `:auto` set (`emi.phase`); the
  governor also always escalates on `:actuation`."
  [db {:keys [subject amount]}]
  (let [w (store/wallet db subject)
        assessment (store/assessment-of db subject)
        evidence-ok? (and assessment (facts/required-evidence-satisfied?
                                       (:jurisdiction w)
                                       (:checklist assessment)))]
    {:summary    (str (:id w) " へ " amount " " (:currency w)
                      " 相当の電子マネー発行準備ができました" (when-not evidence-ok? " (書類未充足)"))
     :rationale  (if assessment
                   (str "spec-basis: " (:spec-basis assessment))
                   "assessment未実施")
     :cites      (if assessment [(:spec-basis assessment)] [])
     :effect     :emoney/issued
     :value      {:amount amount}
     :stake      :actuation
     :confidence (if evidence-ok? 0.9 0.3)}))

(defn- propose-safeguarding
  "Draft the safeguarding-account MOVEMENT action -- confirming that
  `amount` of client funds backing outstanding e-money has been moved to
  the segregated safeguarding account `iban` (EMD2 Art. 7). ALWAYS
  `:stake :actuation` -- a real custodial-account movement, gated exactly
  like `:emoney/issue`. Cites the jurisdiction's official legal basis
  (same G2 discipline `:jurisdiction/assess` uses)."
  [db {:keys [subject iban amount]}]
  (let [w (store/wallet db subject)
        sb (facts/spec-basis (:jurisdiction w))]
    (if sb
      {:summary    (str (:id w) " の保全口座(" iban ") へ " amount " " (:currency w) " を確認")
       :rationale  (str "EMD2 Art.7 保全義務。法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :safeguarding/segregated
       :value      {:iban iban :amount amount}
       :stake      :actuation
       :confidence 0.9}
      {:summary    (str (:jurisdiction w) " の公式spec-basisが見つからず保全確認できません")
       :rationale  "emi.facts に未登録の法域。保全義務の根拠を推測で作らない。"
       :cites      []
       :effect     :safeguarding/segregated
       :value      {:iban iban :amount amount}
       :stake      :actuation
       :confidence 0.2})))

(defn- propose-redemption
  "Draft the REDEMPTION action -- paying out `amount` of e-money at PAR
  VALUE, on demand (EMD2 Art. 11). ALWAYS `:stake :actuation` -- a real
  payout, gated exactly like `:emoney/issue`. A wallet with zero balance,
  or an amount exceeding the on-file balance, has nothing to redeem --
  the governor's hard `:insufficient-balance` check catches that
  independently, but the advisor also declines to propose a confident
  redemption it can already see is oversized."
  [db {:keys [subject amount]}]
  (let [w (store/wallet db subject)
        sb (facts/spec-basis (:jurisdiction w))
        within-balance? (and (number? amount) (<= amount (:balance w 0)))]
    (cond
      (nil? sb)
      {:summary (str (:jurisdiction w) " の公式spec-basisが見つからず償還できません")
       :rationale "emi.facts に未登録の法域。償還手続きの根拠を推測で作らない。"
       :cites [] :effect :redemption/redeemed
       :value {:amount amount}
       :stake :actuation :confidence 0.2}

      (not within-balance?)
      {:summary (str (:id w) " の残高を超える償還は提案できません")
       :rationale "残高不足"
       :cites [] :effect :redemption/redeemed
       :value {:amount amount}
       :stake :actuation :confidence 0.2}

      :else
      {:summary    (str (:id w) " の償還案: " amount " " (:currency w) " (額面通り)")
       :rationale  (str "EMD2 Art.11 償還義務(額面・オンデマンド)。法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :redemption/redeemed
       :value      {:amount amount}
       :stake      :actuation
       :confidence 0.9})))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :wallet/intake          (normalize-intake db request)
    :jurisdiction/assess    (assess-jurisdiction db request)
    :kyc/screen             (screen-kyc db request)
    :emoney/issue            (propose-issuance db request)
    :safeguarding/segregate (propose-safeguarding db request)
    :redemption/redeem      (propose-redemption db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  []
  (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは電子マネー発行機関(EMI)エージェントの助言者です。与えられた事実のみに"
       "基づき、提案を1つだけEDNマップで返します。説明や前置きは一切書かず、"
       "EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:wallet/upsert|:assessment/set|:kyc/set|:emoney/issued|:safeguarding/segregated|:redemption/redeemed) "
       ":stake(:actuation か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "電子マネー残高に金利や与信を付与する提案を絶対にしてはいけません(EMD2違反)。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess {:wallet (store/wallet st subject)}
    :kyc/screen          {:customer (store/customer st subject)}
    :emoney/issue         {:wallet (store/wallet st subject)
                          :assessment (store/assessment-of st subject)}
    :safeguarding/segregate {:wallet (store/wallet st subject)}
    :redemption/redeem   {:wallet (store/wallet st subject)}
    {:wallet (store/wallet st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the EMIGovernor escalates/holds --
  an LLM hiccup can never auto-issue, auto-safeguard or auto-redeem."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :emiadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
