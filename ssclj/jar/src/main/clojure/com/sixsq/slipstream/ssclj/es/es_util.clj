(ns com.sixsq.slipstream.ssclj.es.es-util
  (:refer-clojure :exclude [read update])
  (:require
    [clojure.java.io :as io]
    [clojure.data.json :as json]
    [me.raynes.fs :as fs]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as cu]
    [com.sixsq.slipstream.ssclj.es.es-pagination :as pg]
    [com.sixsq.slipstream.ssclj.es.acl :as acl]
    [com.sixsq.slipstream.ssclj.es.es-order :as od]
    [com.sixsq.slipstream.ssclj.es.es-filter :as ef]
    )
  (:import
    [org.elasticsearch.node NodeBuilder Node]
    [org.elasticsearch.common.settings Settings]
    [org.elasticsearch.common.unit TimeValue]
    [org.elasticsearch.cluster.health ClusterHealthStatus]
    [org.elasticsearch.client Client]
    [org.elasticsearch.action.search SearchType]
    (org.elasticsearch.action.bulk BulkRequestBuilder)      ;; TODO
    (org.elasticsearch.action ActionRequestBuilder)
    (org.elasticsearch.action.admin.indices.delete DeleteIndexRequest)))

;;
;; Elastic Search implementations of CRUD actions
;;

(defn create
  [^Client client index type docid json]
  (println "ES UTILS CREATE " type "/" docid)
  (.. client
      (prepareIndex index type docid)
      (setCreate true)
      (setRefresh true)
      (setSource json)
      (get)
      (isCreated)))

(defn read
  [^Client client index type docid]
  (.. client
      (prepareGet index type docid)
      (get)))

(defn update
  [^Client client index type docid json]
  (.. client
      (prepareUpdate index type docid)
      (setRefresh true)
      (setDoc json)
      (get)))

(defn delete
  [^Client client index type docid]
  (.. client
      (prepareDelete index type docid)
      (get)))

(defn search
  [^Client client index type options]
  (let [query                         (-> options
                                          ef/compile-cimi-filter
                                          (acl/and-acl options))
        [from size]                   (pg/from-size options)
        ^ActionRequestBuilder request (.. client
                                          (prepareSearch (into-array String [index]))
                                          (setTypes (into-array String [type]))
                                          (setSearchType SearchType/DEFAULT)
                                          (setQuery query)
                                          (setFrom from)
                                          (setSize size))
        request-with-sort             (od/add-sorters-from-cimi request options)]
    (.get request-with-sort)))

;;
;; Convenience (for tests) functions
;;

(defn erase-index
  [^Client client index]
  (.. client
      (admin)
      (indices)
      (delete (DeleteIndexRequest. index))
      (get)))


;;
;; Util functions
;;

(defn json->edn [json]
  (when json (json/read-str json :key-fn keyword)))

(defn edn->json [edn]
  (json/write-str edn))

(defn create-test-node
  "Creates a local elasticsearch node which holds data but
   cannot be accessed through the HTTP protocol."
  ([]
   (create-test-node (cu/random-uuid)))

  ([^String cluster-name]
   (let [home     (str (fs/temp-dir "es-data-"))
         settings (.. (Settings/settingsBuilder)
                      (put "http.enabled" false)
                      (put "node.data" true)
                      (put "cluster.name" cluster-name)
                      (put "path.home" home))]
     (.. (NodeBuilder/nodeBuilder)
         (settings settings)
         (local true)
         (node)))))

(def ^:const mapping-not-analyzed
  (-> "mapping-not-analyzed.json"
      io/resource
      slurp))

(defn create-index
  [^Client client index-name]
  (let [settings (.. (Settings/builder)
                     (put "index.max_result_window" pg/max-result-window)
                     (put "index.number_of_shards" 3)
                     (put "index.number_of_replicas" 0))]
    (.. client
        (admin)
        (indices)
        (prepareCreate index-name)
        (setSettings settings)
        (addMapping "_default_" mapping-not-analyzed)
        (get))))

(defn- throw-if-not-green [status]
  (or (= ClusterHealthStatus/GREEN status)
      (throw (ex-info "status is not GREEN" {:status (str status)}))))

(defn cluster-health
  [^Client client indexes]
  (.. client
      (admin)
      (cluster)
      (prepareHealth (into-array String indexes))
      (setWaitForGreenStatus)
      (setTimeout (TimeValue/timeValueSeconds 15))
      (get)))

(defn node-client [^Node node]
  (when node (.client node)))

(defn wait-for-cluster
  [^Client client]
  (let [status (.. (cluster-health client [])
                   (getStatus))]
    (throw-if-not-green status)))

(defn wait-for-index
  [^Client client index]
  (let [status (.. (cluster-health client [index])
                   (getIndices)
                   (get index)
                   (getStatus))]
    (throw-if-not-green status)))