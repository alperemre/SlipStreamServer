(ns com.sixsq.slipstream.ssclj.resources.usage-test
  (:require
    [clojure.test :refer :all]

    [clj-time.core :as time]
    [korma.core :as kc]

    [peridot.core :refer :all]
    [com.sixsq.slipstream.ssclj.db.database-binding :as dbdb]

    [com.sixsq.slipstream.ssclj.resources.common.debug-utils :as du]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]

    [com.sixsq.slipstream.ssclj.api.acl :as acl]
    [com.sixsq.slipstream.ssclj.usage.record-keeper :as rc]
    [com.sixsq.slipstream.ssclj.usage.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.usage :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as t]
    [com.sixsq.slipstream.ssclj.resources.test-utils :as tu :refer [exec-request is-count]]
    [com.sixsq.slipstream.ssclj.db.impl :as db]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as cu]))


(defn- summary
  [user cloud frequency [year month day] usage]
  {:user            user
   :cloud           cloud
   :frequency       (name frequency)
   :start_timestamp (u/timestamp year month day)
   :end_timestamp   (u/timestamp-next-frequency frequency year month day)
   :usage           usage})

(defn insert-daily-summaries
  [f]
  (db/set-impl! (dbdb/get-instance))
  (acl/-init)
  (rc/-init)
  (kc/delete rc/usage_summaries)
  (kc/delete acl/acl)

  (rc/insert-summary! (summary "joe" "exo"   :daily [2015 04 16] {:ram { :unit_minutes 100.0}}))
  (rc/insert-summary! (summary "joe" "exo"   :daily [2015 04 17] {:ram { :unit_minutes 200.0}}))
  (rc/insert-summary! (summary "mike" "aws"  :daily [2015 04 18] {:ram { :unit_minutes 500.0}}))
  (rc/insert-summary! (summary "mike" "exo"  :daily [2015 04 16] {:ram { :unit_minutes 300.0}}))
  (rc/insert-summary! (summary "mike" "aws"  :daily [2015 04 17] {:ram { :unit_minutes 40.0}}))

  (rc/insert-summary! (summary "joe"  "exo"   :weekly [2015 04 15] {:ram { :unit_minutes 300.0}}))
  (rc/insert-summary! (summary "mike" "aws"   :weekly [2015 04 15] {:ram { :unit_minutes 540.0}}))
  (rc/insert-summary! (summary "mike" "exo"   :weekly [2015 04 15] {:ram { :unit_minutes 300.0}}))

  (rc/insert-summary! (summary "joe"  "exo"   :monthly [2015 04 15] {:ram { :unit_minutes 300.0}}))
  (rc/insert-summary! (summary "mike" "aws"   :monthly [2015 04 15] {:ram { :unit_minutes 540.0}}))
  (rc/insert-summary! (summary "mike" "exo"   :monthly [2015 04 15] {:ram { :unit_minutes 300.0}}))

  (f))

(use-fixtures :once insert-daily-summaries)

(def base-uri (str p/service-context (cu/de-camelcase resource-name)))

(defn are-desc-dates?
  [m]
  (->> (get-in m [:response :body :usages])
       (map :end_timestamp)
       tu/ordered-desc?
       is)
  m)

(defn are-all-usages?
  [m field expected]
  (->> (get-in m [:response :body :usages])
       (map field)
       distinct
       (= [expected])
       is)
  m)

(deftest get-should-return-most-recent-first-by-user
  (-> (exec-request base-uri "?$filter=frequency='daily'" "joe")
      (t/is-key-value :count 2)
      are-desc-dates?
      (are-all-usages? :user "joe"))

  (-> (exec-request base-uri "?$filter=frequency='daily'" "mike")
      (t/is-key-value :count 3)
      are-desc-dates?
      (are-all-usages? :user "mike")))

(deftest acl-filter-cloud-with-role
  (-> (exec-request base-uri "?$filter=frequency='daily'" "john exo1 exo")
      (t/is-key-value :count 3)
      are-desc-dates?
      (are-all-usages? :cloud "exo")))

(defn last-uuid
  []
  (let [full-uuid (-> (kc/select rc/usage_summaries (kc/limit 1))
                      first
                      :id)
        uuid (-> full-uuid
                 (superstring.core/split #"/")
                 second)]
    [uuid full-uuid]))

(deftest get-uuid-with-correct-authn
  (let [[uuid full-uuid] (last-uuid)]
    (-> (exec-request (str base-uri "/" uuid) ""  "john exo")
        (t/is-key-value :id full-uuid)
        (t/is-status 200))))

(deftest get-uuid-without-correct-authn
  (let [[uuid _] (last-uuid)]
    (t/is-status (exec-request (str base-uri "/" uuid) "" "intruder") 403)))

(def ^:private are-counts
  (partial tu/are-counts :usages base-uri))

(def ^:private are-counts-for-admin
  (partial tu/are-counts :usages base-uri "super ADMIN"))

(deftest pagination-full
  (are-counts "mike" 3 "?$filter=frequency='daily'&$first=1&$last=10")
  (are-counts "mike" 2 "?$filter=frequency='weekly'&$first=1&$last=10")
  (are-counts "mike" 2 "?$filter=frequency='monthly'&$first=1&$last=10"))

(deftest pagination-only-one
  (are-counts "mike" 3 1 "?$filter=frequency='daily'&$first=1&$last=1")
  (are-counts "mike" 2 1 "?$filter=frequency='weekly'&$first=1&$last=1")
  (are-counts "mike" 2 1 "?$filter=frequency='monthly'&$first=1&$last=1"))

(deftest pagination-outside-bounds
  (are-counts "mike" 3 0 "?$filter=frequency='daily'&$first=10&$last=15")
  (are-counts "mike" 2 0 "?$filter=frequency='weekly'&$first=10&$last=15")
  (are-counts "mike" 2 0 "?$filter=frequency='monthly'&$first=10&$last=15"))

(deftest pagination-first-larger-than-last
  (are-counts "mike" 3 0 "?$filter=frequency='daily'&$first=10&$last=5")
  (are-counts "mike" 2 0 "?$filter=frequency='weekly'&$first=10&$last=5")
  (are-counts "mike" 2 0 "?$filter=frequency='monthly'&$first=10&$last=5"))

(defn- expect-pagination
  [code query-strings]
  (doseq [query-string query-strings]
    (t/is-status (exec-request base-uri query-string "mike") code)))

(deftest pagination-wrong-query-ignores-invalid
  (expect-pagination 200
      ["?$first=a&$last=10"])
  (expect-pagination 200
      ["?$first=-1&$last=10"
      "?$first=1&$last=-10"
      "?$first=-1&$last=-10"]))

(deftest pagination-does-not-check-max-limit
  (expect-pagination 200
    ["?$first=1&$last=1000000"]))

(deftest admin-sees-everything
  (are-counts-for-admin 11 "")
  (are-counts-for-admin 5 "?$filter=frequency='daily'")
  (are-counts-for-admin 3 "?$filter=frequency='weekly'")
  (are-counts-for-admin 3 "?$filter=frequency='monthly'"))

(deftest simple-filter-with-admin
  (are-counts-for-admin 2 "?$filter=frequency='daily'&$filter=user='joe'"  )
  (are-counts-for-admin 3 "?$filter=frequency='daily'&$filter=user='mike'" ))

(deftest filter-int-value-when-no-value
  (are-counts-for-admin 0 "?$filter=xxx<100"))

(deftest filter-int-value
  (are-counts-for-admin 1 "?$filter=frequency='daily' and usage/ram/unit_minutes<100")
  (are-counts-for-admin 1 "?$filter=frequency='daily' and usage/ram/unit_minutes > 400")
  (are-counts-for-admin 1 "?$filter=frequency='daily' and usage/ram/unit_minutes < 50")
  (are-counts-for-admin 1 "?$filter=frequency='daily' and usage/ram/unit_minutes < 50 and usage/ram/unit_minutes > 30")
  (are-counts-for-admin 2 "?$filter=frequency='daily' and usage/ram/unit_minutes > 100 and usage/ram/unit_minutes < 500")

  (are-counts-for-admin 1 "?$filter=frequency='daily' and usage/ram/unit_minutes = 40")
  (are-counts-for-admin 1 "?$filter=frequency='daily' and usage/ram/unit_minutes = 100")
  (are-counts-for-admin 1 "?$filter=frequency='daily' and usage/ram/unit_minutes = 200")
  (are-counts-for-admin 1 "?$filter=frequency='daily' and usage/ram/unit_minutes = 300")
  (are-counts-for-admin 1 "?$filter=frequency='daily' and usage/ram/unit_minutes = 500"))

(defn- one-line
  [s]
  (superstring.core/replace s #"\n" ""))

(deftest filter-with-admin
  (are-counts-for-admin 2 (one-line
                "?$filter=
                 start_timestamp='2015-04-16T00:00:00.000Z'
                 and
                 end_timestamp='2015-04-17T00:00:00.000Z'"))

  (are-counts-for-admin 1 (one-line
                "?$filter=
                 user='joe'
                 and
                 start_timestamp='2015-04-16T00:00:00.000Z'
                 and
                 end_timestamp='2015-04-17T00:00:00.000Z'"))

  (are-counts-for-admin 1 (one-line
                "?$filter=
                 user='joe'
                 and
                 start_timestamp='2015-04-17T00:00:00.000Z'
                 and
                 end_timestamp='2015-04-18T00:00:00.000Z'"))

  (are-counts-for-admin 0 (one-line
              "?$filter=
               user='joe'
               and
               start_timestamp='2015-04-18T00:00:00.000Z'
               and
               end_timestamp='2015-04-19T00:00:00.000Z'"))

  (are-counts-for-admin 1 (one-line
              "?$filter=
               user='mike'
               and
               start_timestamp='2015-04-18T00:00:00.000Z'
               and
               end_timestamp='2015-04-19T00:00:00.000Z'")))

(deftest date-comparisons
  (are-counts-for-admin 1 "?$filter=user='joe' and start_timestamp=2015-04-17 and end_timestamp=2015-04-18")
  (are-counts-for-admin 1 "?$filter=user='joe' and start_timestamp=2015-04-16 and end_timestamp=2015-04-17")
  (are-counts-for-admin 2 "?$filter=frequency='daily' and user='joe' and start_timestamp>2015-04-15"))
