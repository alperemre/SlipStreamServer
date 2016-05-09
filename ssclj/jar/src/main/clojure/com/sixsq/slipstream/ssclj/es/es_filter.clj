(ns com.sixsq.slipstream.ssclj.es.es-filter
  (:require
    [clojure.walk :as w]
    [superstring.core :as s]
    [com.sixsq.slipstream.ssclj.filter.parser :as p]
    [com.sixsq.slipstream.ssclj.usage.utils :as uu]
    )
  (:import
    [org.elasticsearch.index.query QueryBuilders ConstantScoreQueryBuilder]
    [java.util Date]))

(defn term-query [^String term ^Object value]
  (QueryBuilders/termQuery term value))

(defn range-ge-query [^String term ^Object value]
  (.. (QueryBuilders/rangeQuery term)
      (gte value)))

(defn range-gt-query [^String term ^Object value]
  (.. (QueryBuilders/rangeQuery term)
      (gt value)))

(defn range-le-query [^String term ^Object value]
  (.. (QueryBuilders/rangeQuery term)
      (lte value)))

(defn range-lt-query [^String term ^Object value]
  (.. (QueryBuilders/rangeQuery term)
      (lt value)))

(defn and-query [clauses]
  (let [q (QueryBuilders/boolQuery)]
    (doall (map #(.must q %) clauses))
    q))

(defn or-query
  "The OR is handled by a boolean query with only 'should' clauses.
   In this case, elasticsearch treats this specially and requires at
   least one of the included 'should' queries to pass."
  [clauses]
  (let [q (QueryBuilders/boolQuery)]
    (doall (map #(.should q %) clauses))
    q))

(defn strip-quotes [s]
  (s/substring s 1 (- (s/length s) 1)))

(defmulti convert
          (fn [v]
            (when (vector? v)
              (first v))))

(defmethod convert :IntValue [[_ ^String s]]
  [:Value (Integer/valueOf s)])

(defmethod convert :DoubleQuoteString [[_ s]]
  [:Value (strip-quotes s)])

(defmethod convert :SingleQuoteString [[_ s]]
  [:Value (strip-quotes s)])

(defmethod convert :BoolValue [[_ ^String s]]
  [:Value (Boolean/valueOf s)])

(defmethod convert :DateValue [[_ ^String s]]
  [:Value (uu/to-time s)])

(defmethod convert :Comp [v]
  (let [args (rest v)]
    (if (= 1 (count args))
      (first args)                                          ;; (a=1 and b=2) case
      (let [{:keys [Attribute Op Value] :as m} (into {} args)
            order (ffirst args)]
        (case [Op order]
          ["=" :Attribute] (term-query Attribute Value)
          [">=" :Attribute] (range-ge-query Attribute Value)
          [">" :Attribute] (range-gt-query Attribute Value)
          ["<=" :Attribute] (range-le-query Attribute Value)
          ["<" :Attribute] (range-lt-query Attribute Value)
          ["=" :Value] (term-query Attribute Value)
          [">=" :Value] (range-le-query Attribute Value)
          [">" :Value] (range-lt-query Attribute Value)
          ["<=" :Value] (range-ge-query Attribute Value)
          ["<" :Value] (range-gt-query Attribute Value)
          m)))))

(defmethod convert :PropExpr [[_ Prop Op Value]]
  (let [result [[:Attribute (str "property." (second Prop))] Op Value]]
    (println result)
    result))

(defmethod convert :AndExpr [v]
  (let [args (rest v)]
    (if (= 1 (count args))
      (first args)
      (and-query args))))

(defmethod convert :Filter [v]
  (let [args (rest v)]
    (if (= 1 (count args))
      (first args)
      (or-query args))))

(defmethod convert :Attribute [v]
  [:Attribute (-> v second (s/replace "/" "."))])

(defmethod convert :default [v]
  v)

(defn compile-filter [s]
  (let [parsed (p/parse-cimi-filter s)]
    (clojure.pprint/pprint parsed)
    (QueryBuilders/constantScoreQuery (w/postwalk convert parsed))))

(defn compile-cimi-filter [cimi-params]
  (if-let [cimi-filter (get-in cimi-params [:cimi-params :filter])]
    (compile-filter cimi-filter)
    (QueryBuilders/matchAllQuery)))
