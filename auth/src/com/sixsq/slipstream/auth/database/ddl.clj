(ns com.sixsq.slipstream.auth.database.ddl
  (:refer-clojure :exclude [update])
  (:require
    [clojure.tools.logging                                        :as log]
    [clojure.java.jdbc                                :refer :all :as jdbc]
    [clojure.string                                   :refer [join split]]
    [com.sixsq.slipstream.auth.database.korma-helper              :as kh]))

(defn simple-surrounder
  [c]
  (fn sur [s]
    (str c s c)))

(defn surrounder
  [c]
  (fn [s]
    (->>  (split s #"\.")
          (map (simple-surrounder c))
          (join "."))))

(def simple-quote (surrounder \'))
(def double-quote (surrounder \"))

(defn- column-description
  [[name type]]
  (str (double-quote name) " " type))

(defn surround-join
  [xs surround]
  (->>  xs
        (map surround)
        (join ",")))

(defn double-quote-list
  [names]
  (surround-join names double-quote))

(defn columns
  [& name-types]
  (->>  name-types
        (partition 2)
        (map column-description)
        (join ",")))

(defn create-table!
  [table columns & [options]]
  (jdbc/execute! (kh/db-spec) [(str "CREATE TABLE IF NOT EXISTS " (double-quote table) " ( " columns options " ) ")])
  (log/info "Created (if needed!) table:" table ", columns:" columns ", options: "options))

(defn create-index!
  [table index-name & column-names]
  (jdbc/execute! (kh/db-spec) [(str "DROP INDEX IF EXISTS " index-name)])
  (jdbc/execute! (kh/db-spec) [(str "CREATE INDEX " index-name " ON \""table"\" (" (double-quote-list column-names) ")")])
  (log/info "Created index on" table ": " index-name ", " column-names))