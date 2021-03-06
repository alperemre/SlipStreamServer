(ns com.sixsq.slipstream.db.serializers.service-config-util
  (:require
    [clojure.data.xml :as xml]
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.db.serializers.utils :as u]
    [me.raynes.fs :as fs]))

(defn- xml-params
  [xml]
  (-> xml :content first :content))

(defn- xml-param-elems
  [p]
  (-> p :content second :content))

(defn- filter-by-tag
  [tag vals]
  (filter #(= (:tag %) tag) vals))

(defn xml-param
  [p tag]
  (->> p
       xml-param-elems
       (filter-by-tag tag)
       first
       :content
       first))

(defn xml-param-attrs
  [p]
  (-> p :content second :attrs))

(defn xml-param-value
  [p]
  (xml-param p :value))

(defn xml-param-instructions
  [p]
  (xml-param p :instructions))

(defn xml-param-enums
  [content]
  (->> content
       (filter-by-tag :enumValues)
       first))

(defn xml-param-enum-values
  [p]
  (if-let [enums (-> p xml-param-elems xml-param-enums)]
    (map #(-> % :content first) (:content enums))
    []))

(defn xml-params-parse
  "Returns [[{attributes} \"value\" \"instruction\" [enumValues]] ..]"
  [xml]
  (map (juxt xml-param-attrs xml-param-value xml-param-instructions xml-param-enum-values) (xml-params xml)))

(defn conf-xml->sc
  "xml-conf - SlipStream service configuration as XML string."
  [xml-conf serviceConf]
  (let [xml-data (xml/parse-str xml-conf)]
    (doseq [[attrs value instructions enum-vals] (xml-params-parse xml-data)]
      (let [desc (merge attrs {:instructions instructions
                               :enum         enum-vals})]
        (.setParameter serviceConf (u/build-sc-param serviceConf value desc))))
    serviceConf))

(defn sc-get-param-value
  "sc - ServiceConfiguration
  pname - str (parameter name)"
  [sc pname]
  (if-let [p (.getParameter sc pname)]
      (.getValue p)))

(defn spit-pprint
  [obj fpath]
  (let [f (fs/expand-home fpath)]
    (with-open [^java.io.Writer w (apply clojure.java.io/writer f {})]
      (clojure.pprint/pprint obj w))))

