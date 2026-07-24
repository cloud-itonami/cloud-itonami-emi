(ns emi.facts
  "Per-jurisdiction electronic-money-institution (EMI) licensing catalog --
  the G2-style spec-basis table the EMIGovernor checks every
  `:jurisdiction/assess` (and every actuation) proposal against ('did the
  advisor cite an OFFICIAL public source for this jurisdiction's e-money
  issuance / safeguarding regime, or did it invent one?').

  This is the EMI analog of `cloud-itonami-isic-6910`'s `formation.facts`
  (company-formation requirement catalog) and `cloud-itonami-isic-6419`'s
  `banking.facts` (AML/KYC catalog) -- same honesty discipline, different
  domain: here the spec-basis is each jurisdiction's e-money-ISSUANCE
  authorization regime (who may issue e-money, and what customer-due-
  diligence + safeguarding evidence a wallet needs before real e-money can
  be issued against it), not a banking deposit-taking license and not a
  company-registry filing.

  Coverage is reported HONESTLY (see `coverage`): a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official payments/e-money
  supervisory authority and its e-money-institution law (see
  `:provenance`); they are a STARTING catalog, not a from-scratch survey
  of all ~194 jurisdictions. Extending coverage is additive: add one map
  to `catalog`, cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.

  One entry (`\"USA\"`) is deliberately a STRUCTURAL-DIFFERENCE note, the
  same honest-mismatch discipline `banking.facts`' `\"ARE\"` entry uses:
  the United States has no federal 'electronic money institution' license
  category at all (unlike the EU's EMD2-derived EMI license) -- e-money-
  like stored value is regulated as MONEY TRANSMISSION, licensed per
  STATE, plus federal FinCEN Money Services Business (MSB) registration
  under the Bank Secrecy Act. Folding that into a fake 'USA EMI license'
  entry would be exactly the fabrication this catalog exists to prevent."
  )

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  identity-verification-record/source-of-funds-record/safeguarding-
  account-confirmation/sanctions-screening-record evidence set every
  sibling actor's evidence checklist submits in some form; `:legal-basis`
  / `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any `:emoney/issue`/`:safeguarding/segregate`/
  `:redemption/redeem` proposal can commit."
  {"GBR" {:name "United Kingdom"
          :owner-authority "Financial Conduct Authority (FCA)"
          :legal-basis "Electronic Money Regulations 2011 (SI 2011/99)"
          :national-spec "FCA Electronic Money Institution (EMI) authorisation regime"
          :provenance "https://www.fca.org.uk/firms/electronic-money-institutions"
          :required-evidence ["Identity-verification record"
                              "Source-of-funds record"
                              "Safeguarding-account confirmation"
                              "Sanctions-screening record"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesanstalt für Finanzdienstleistungsaufsicht (BaFin)"
          :legal-basis "Zahlungsdiensteaufsichtsgesetz (ZAG) -- E-Geld-Institut licensing"
          :national-spec "BaFin E-Geld-Institut (e-money institution) authorisation"
          :provenance "https://www.bafin.de/DE/Aufsicht/BankenFinanzdienstleister/EGeldinstitute/e_geldinstitute_node.html"
          :required-evidence ["Identitätsprüfungsprotokoll (identity-verification-record)"
                              "Mittelherkunftsnachweis (source-of-funds-record)"
                              "Sicherungskonto-Bestätigung (safeguarding-account-confirmation)"
                              "Sanktionslisten-Screening-Protokoll (sanctions-screening-record)"]}
   "FRA" {:name "France"
          :owner-authority "Autorité de Contrôle Prudentiel et de Résolution (ACPR)"
          :legal-basis "Code monétaire et financier, Art. L526-1 et seq. (établissement de monnaie électronique)"
          :national-spec "ACPR agrément d'établissement de monnaie électronique"
          :provenance "https://acpr.banque-france.fr/autoriser/procedures-secteur-financier/etablissements-de-monnaie-electronique"
          :required-evidence ["Identity-verification record"
                              "Source-of-funds record"
                              "Safeguarding-account confirmation"
                              "Sanctions-screening record"]}
   "LTU" {:name "Lithuania"
          :owner-authority "Lietuvos bankas (Bank of Lithuania)"
          :legal-basis "Law on Electronic Money and Electronic Money Institutions of the Republic of Lithuania"
          :national-spec "Lietuvos bankas electronic-money-institution (EMI) licence"
          :provenance "https://www.lb.lt/en/emi-e-money-institutions"
          :required-evidence ["Identity-verification record"
                              "Source-of-funds record"
                              "Safeguarding-account confirmation"
                              "Sanctions-screening record"]}
   "IRL" {:name "Ireland"
          :owner-authority "Central Bank of Ireland"
          :legal-basis "European Communities (Electronic Money) Regulations 2011 (S.I. No. 183 of 2011)"
          :national-spec "Central Bank of Ireland electronic money institution authorisation"
          :provenance "https://www.centralbank.ie/regulation/how-we-regulate/authorisation/electronic-money-institutions"
          :required-evidence ["Identity-verification record"
                              "Source-of-funds record"
                              "Safeguarding-account confirmation"
                              "Sanctions-screening record"]}
   "JPN" {:name "Japan"
          :owner-authority "金融庁 (Financial Services Agency, FSA)"
          :legal-basis "資金決済に関する法律 (Payment Services Act) -- 前払式支払手段発行者登録 (prepaid-payment-instrument-issuer registration)"
          :national-spec "資金決済法に基づく前払式支払手段/資金移動業の発行者規制"
          :provenance "https://www.fsa.go.jp/policy/kessai/index.html"
          :required-evidence ["本人確認記録 (identity-verification-record)"
                              "資金源確認記録 (source-of-funds-record)"
                              "供託/保全措置確認記録 (safeguarding-account-confirmation)"
                              "制裁リストスクリーニング記録 (sanctions-screening-record)"]}
   "SGP" {:name "Singapore"
          :owner-authority "Monetary Authority of Singapore (MAS)"
          :legal-basis "Payment Services Act 2019 -- Major Payment Institution (e-money issuance service) licence"
          :national-spec "MAS Payment Services Act e-money-issuance-service licensing regime"
          :provenance "https://www.mas.gov.sg/regulation/payments/payment-service-providers"
          :required-evidence ["Identity-verification record"
                              "Source-of-funds record"
                              "Safeguarding-account confirmation"
                              "Sanctions-screening record"]}
   ;; USA deliberately does NOT mirror the EMD2-style "electronic money
   ;; institution" shape the other seven entries share -- see ns
   ;; docstring. This entry covers the money-transmission STRUCTURE
   ;; (federal MSB registration + the fact that the operative license is
   ;; issued per-state, not nationally) rather than inventing a
   ;; nonexistent unified "USA EMI license". Any real US deployment needs
   ;; every state's own money-transmitter statute as its own catalog
   ;; entry (out of scope for this starting seed) -- `:required-evidence`
   ;; here is the FEDERAL floor (FinCEN MSB registration under the Bank
   ;; Secrecy Act) common to all of them, not a complete state-by-state
   ;; requirement set.
   "USA" {:name "United States (federal floor only -- see ns docstring; state money-transmitter licensing is additional and NOT covered by this entry)"
          :owner-authority "Financial Crimes Enforcement Network (FinCEN)"
          :legal-basis "Bank Secrecy Act (BSA), 31 U.S.C. § 5311 et seq. -- Money Services Business (MSB) registration; e-money-like stored value is licensed as MONEY TRANSMISSION at the state level, not under any federal 'EMI' category"
          :national-spec "FinCEN MSB registration (federal) + per-state money-transmitter licensing (not itemized here)"
          :provenance "https://www.fincen.gov/money-services-business-definition"
          :required-evidence ["Identity-verification record"
                              "Source-of-funds record"
                              "Safeguarding-account confirmation"
                              "Sanctions-screening record"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to issue e-money,
  segregate safeguarding funds, or redeem e-money on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-emi R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `emi.facts/catalog`, never "
                 "fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
