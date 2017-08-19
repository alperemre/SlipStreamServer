(ns com.sixsq.slipstream.ssclj.middleware.cimi-params.impl-test
  (:require
    [clojure.test :refer [deftest is are]]
    [com.sixsq.slipstream.ssclj.filter.parser :as parser]
    [com.sixsq.slipstream.ssclj.middleware.cimi-params.impl :as t]))

(deftest check-params->size
  (are [expect params] (= expect (t/cimi-size params))
                       t/max-size {}
                       t/max-size {"$size" "bad"}
                       t/max-size {"$size" ["bad" "invalid"]}
                       t/max-size {"$size" (* 2 t/max-size)}
                       t/max-size {"$size" ["bad" (* 2 t/max-size)]}
                       0 {"$size" -1}
                       0 {"$size" [-1 10]}
                       10 {"$size" [10 "bad" -1]}))

(deftest check-params->first
  (are [expect arg] (= expect (t/cimi-first {"$first" arg}))
                    1 nil
                    1 "a"
                    1 ["a" "b"]
                    10 10
                    10 [10]
                    10 ["a" 10]
                    10 [10 "a"]))

(deftest check-params->last
  (are [expect arg] (= expect (t/cimi-last {"$last" arg}))
                    nil nil
                    nil "a"
                    nil ["a" "b"]
                    10 10
                    10 [10]
                    10 ["a" 10]
                    10 [10 "a"]))

(deftest check-params->format
  (are [expect arg] (= expect (t/cimi-format {"$format" arg}))
                    "application/json" "json"
                    "application/json" "JSON"
                    "application/json" " JSON "
                    "application/xml" "xml"
                    "application/xml" "XML"
                    "application/xml" " XML "
                    "application/edn" "edn"
                    "application/edn" "EDN"
                    "application/edn" " EDN "
                    nil "unknown"
                    nil nil
                    "application/json" ["json" "xml"]
                    "application/json" ["unknown" "json" "xml"]))

(deftest check-params->select
  (are [expect arg] (= expect (t/cimi-select {"$select" arg}))
                    nil nil
                    nil "*"
                    #{"a" "resourceURI"} "a"
                    #{"a" "resourceURI"} " a "
                    #{"a" "resourceURI"} "a,a"
                    #{"a" "resourceURI"} [" a,a" "a" "a"]
                    #{"a" "a2" "resourceURI"} " a, a2 "))

(deftest check-params->expand
  (are [expect arg] (= expect (t/cimi-expand {"$expand" arg}))
                    :none nil
                    :all "*"
                    #{"a"} "a"
                    #{"a" "b"} "a,b"
                    #{"a" "b"} " a , b "
                    #{"a" "b"} ["a" "b"]))

(deftest check-params->orderby
  (are [expect arg] (= expect (t/cimi-orderby {"$orderby" arg}))
                    [] nil
                    [["a" :asc]] "a:asc"
                    [["a" :desc]] "a:desc"
                    [["a" :desc] ["b" :asc]] "a:desc,b"
                    [["a" :desc] ["b" :desc]] ["a:desc" "b:desc"]
                    [["a" :desc] [":b" :asc]] ["a:desc" ":b"]
                    [["a" :desc] ["b" :desc]] [" a :desc " "b :desc"]))

(deftest check-params->filter
  (are [arg1 arg2] (= (parser/parse-cimi-filter arg1) (t/cimi-filter {"$filter" arg2}))
                   "(a=1)" "a=1"
                   "(a=1)" ["a=1"]
                   "(a=1) and (b=2)" ["a=1" "b=2"]
                   "(a=1) and (b=2) and (c=3)" ["a=1" "b=2" "c=3"]
                   "(a=1 or c=3) and (b=2)" ["a=1 or c=3" "b=2"]))

(deftest check-params->metric
  (is (= {:sum ["attr0" "attr1" "attr4"]
          :min ["attr2"]
          :max ["attr3"]}
         (t/cimi-metric {"$metric" ["attr0:sum,attr1:sum" "invalid" "attr2:min" "attr3:max" "attr4:sum"]}))))
