(ns com.sixsq.slipstream.db.es.es-aggregators
  (:import
    (org.elasticsearch.search.aggregations AggregationBuilders AggregationBuilder)
    (org.elasticsearch.action.search SearchRequestBuilder)))

(defmacro aggregator-constructor-fn
  "This takes a form that must evaluate to a string at compile time. The
   string is used to create an aggregator constructor function that will take a
   field name as the argument."
  [algo-form]
  (let [class (symbol "org.elasticsearch.search.aggregations.AggregationBuilders")
        algo-name (eval algo-form)
        algo-sym (symbol algo-name)]
    `(fn [field#]
       (let [fn-name# (str ~algo-name ":" field#)]
         (doto (. ~class ~algo-sym fn-name#)
           (.field field#))))))

(def aggregator-constructors {:min (aggregator-constructor-fn "min")
                              :max (aggregator-constructor-fn "max")
                              :sum (aggregator-constructor-fn "sum")
                              :avg (aggregator-constructor-fn "avg")})

(defn add-aggregator [^SearchRequestBuilder builder [algo-kw fields]]
  (when-let [f (aggregator-constructors algo-kw)]
    (doseq [field fields]
      (let [^AggregationBuilder aggregator (f field)]
        (.addAggregation builder aggregator))))
  builder)

(defn add-aggregators
    "Adds aggregators to the search request builder from the value of the
     metric parameter. This parameter has keys (as keywords) that identifies
     the algorithm to use and values that are vectors of field names."
    [^SearchRequestBuilder builder {{:keys [metric]} :cimi-params :as options}]
    (reduce add-aggregator builder metric))
