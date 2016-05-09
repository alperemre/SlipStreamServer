;;
;; Elastic Search implementation of Binding protocol
;;
(ns com.sixsq.slipstream.ssclj.es.es-binding
  (:require
    [ring.util.response :as r]
    [superstring.core :as s]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as cu]
    [com.sixsq.slipstream.ssclj.es.es-util :as esu]
    [com.sixsq.slipstream.ssclj.es.acl :as acl]
    [com.sixsq.slipstream.ssclj.resources.common.debug-utils :as du]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u])
  (:import (com.sixsq.slipstream.ssclj.db.binding Binding)
           (org.elasticsearch.index.engine DocumentAlreadyExistsException)
           (clojure.lang ExceptionInfo)))

(def ^:const index "resources-index")

(defn create-client
  []
  (println "Creating ES client")
  (let [node    (esu/create-test-node)
        client  (esu/node-client node)]
    (esu/wait-for-cluster client)
    (esu/create-index client index)
    (esu/wait-for-index client index)
    client))

(def client (create-client))

(defn- force-admin-role-right-all
  [data]
  (update-in data [:acl :rules] #(vec (set (conj % {:type "ROLE" :principal "ADMIN" :right "ALL"})))))

(defn- split-id
  [id]
  (s/split id #"/"))

;; TODO
(defn- data->doc
  "Prepares data before insertion in index
  That includes
  - add ADMIN role with right ALL
  - denormalize ACLs
  - jsonify "
  [type data]
  (let [

        ;uuid  (if (:id data) "1" (cu/random-uuid))
        ;id    (str (u/de-camelcase type) "/" uuid)

        [id uuid] [(:id data) (second (split-id (:id data)))]

        json  (-> data
                  force-admin-role-right-all
                  acl/denormalize-acl
                  (assoc :id id)
                  esu/edn->json)]
    [id uuid json]))

(defn doc->data
  "Converts to edn and renormalize ACLs"
  [doc]
  (-> doc
      esu/json->edn
      acl/normalize-acl))

(defn- response-created
  [id]
  (-> (str "created " id)
      (cu/map-response 201 id)
      (r/header "Location" id)))

;; TODO
;; 409 if already existing
;; other code when appropriate
(defn response-error
  []
  (-> "Resource not created"
      (cu/map-response 500 nil)))

(defn response-conflict
  [id]
  (cu/ex-conflict id))

(defn- response-deleted
  [id]
  (cu/map-response (str id " deleted") 204 id))

(defn- response-updated
  [id]
  (cu/map-response (str "updated " id) 200 id))

(defn response-exception
  [ei]
  (ex-data ei))

(defn- check-identity-present
  [options]
  )
  ;; TODO ??
  ;; (println "bypassing check-identity-present options"))
  ;(clojure.pprint/pprint options)
  ;(when (and (empty? (:user-name options))
  ;           (every? empty? (:user-roles options)))
  ;  (throw (IllegalArgumentException. "A non empty user name or user role is mandatory."))))


(defn find-data
  [client index id options action]
  (check-identity-present options)
  (let [[type docid] (split-id id)]
    (-> (esu/read client index type docid)
        (.getSourceAsString)
        doc->data
        (acl/check-can-do options action))))

(deftype ESBinding []
  Binding
  (add [_ type data options]
    (let [[id uuid json] (data->doc type data)]
      (try
        (if (esu/create client index (u/de-camelcase type) uuid json)
          (response-created id)
          (response-error))
        (catch DocumentAlreadyExistsException e
          (response-conflict id)))))

  (retrieve [_ id options]
    ;; (check-exist id) TODO equivalent
    (println "ES binding retrieving " id " options" options)
    (find-data client index id options "VIEW"))

  (delete [_ {:keys [id]} options]
    ;; (check-exist id) TODO equivalent
    (find-data client index id options "ALL")
    (let [[type docid] (split-id id)]
      (.isFound (esu/delete client index type docid)))
    (response-deleted id))

  (edit [_ {:keys [id] :as data} options]

    (try
      ;; (check-exist id) TODO equivalent
      (find-data client index id options "MODIFY")

      (let [[type docid] (split-id id)]
        (if (esu/update client index type docid (esu/edn->json data))
          (r/response data)
          (response-conflict id)))
      (catch ExceptionInfo ei
        (println "CAUGHT EI " (ex-data ei))
        (response-exception ei))))

  (query [_ collection-id options]
    (check-identity-present options)
    ;; (println "query options " options)
    (let [response (esu/search client index collection-id options)
          result (esu/json->edn (str response))
          count-before-pagination (-> result :hits :total)
          hits (->> (-> result :hits :hits)
                    (map :_source)
                    (map acl/normalize-acl))]
      [count-before-pagination hits])))

(defn get-instance
  []
  (ESBinding.))
