(ns com.sixsq.slipstream.ssclj.resources.deployment-parameter
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as string]
    [com.sixsq.slipstream.ssclj.resources.spec.deployment-parameter]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.auth.acl :as a]
    [superstring.core :as str]
    [com.sixsq.slipstream.util.response :as r]
    [com.sixsq.slipstream.db.impl :as db]
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.ssclj.util.log :as logu]
    [com.sixsq.slipstream.ssclj.util.sse :as sse]
    [clojure.core.async :as async]
    [zookeeper :as zk]
    [com.sixsq.slipstream.ssclj.util.zookeeper :as uzk]
    [com.sixsq.slipstream.ssclj.resources.zk.deployment.utils :as zdu]
    )
  (:import (clojure.lang ExceptionInfo)))

(def ^:const resource-name "DeploymentParameter")

(def ^:const resource-tag (keyword (str (str/camel-case resource-name) "s")))

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "DeploymentParameterCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})

;;
;; multimethods for validation and operations
;;

(def validate-fn (u/create-spec-validation-fn :cimi/deployment-parameter))
(defmethod crud/validate resource-uri
  [resource]
  (validate-fn resource))

(defmethod crud/add-acl resource-uri
  [resource request]
  (a/add-acl resource request))

;;
;; CRUD operations
;;

(def deployment-parameter-id-separator "_")

(defn deployment-parameter-id
  [{deployment-href :deployment-href node-name :node-name node-index :node-index name :name :as deployment-parameter}]
  (let [deployment-href (string/replace-first deployment-href #"^deployment/" "")]
    (cond
      (and deployment-href node-name node-index) (string/join deployment-parameter-id-separator [deployment-href node-name node-index name])
      (and deployment-href node-name) (string/join deployment-parameter-id-separator [deployment-href node-name name])
      deployment-href (string/join deployment-parameter-id-separator [deployment-href name]))))

(defmethod crud/new-identifier resource-name
  [json resource-name]
  (let [new-id (str resource-url "/" (deployment-parameter-id json))]
    (assoc json :id new-id)))

(defn add-value-deployment-parameter [deployment-parameter & {:keys [watcher]}]
  (try
    (let [value (if watcher
                  (uzk/get-data (zdu/deployment-parameter-znode-path deployment-parameter) :watcher watcher)
                  (uzk/get-data (zdu/deployment-parameter-znode-path deployment-parameter)))]
      (assoc deployment-parameter :value value))
    (catch ExceptionInfo ei                                 ;TODO what if data not found
      (ex-data ei))))

(defn add-impl [{:keys [body] :as request}]
  (a/can-modify? {:acl collection-acl} request)
  (let [deployment-parameter (-> body
                                 u/strip-service-attrs
                                 (crud/new-identifier resource-name)
                                 (assoc :resourceURI resource-uri)
                                 u/update-timestamps
                                 (crud/add-acl request)
                                 crud/validate)
        response (db/add resource-name deployment-parameter {})
        value (:value body)
        node-path (zdu/deployment-parameter-znode-path body)]
    (uzk/create-all node-path :persistent? true)
    (uzk/set-data node-path value)
    response))

(defn transiant-watch-fn [event-ch id name {:keys [event-type path :as zk-event]}]
  (when (= event-type :NodeDataChanged)
    (let [deployment-parameter (-> (db/retrieve id {})
                                   (add-value-deployment-parameter))]
      (sse/send-event id name deployment-parameter event-ch)
      (async/close! event-ch))))

(defn persistent-watch-fn [event-ch id name {:keys [event-type path :as zk-event]}]
  (when (= event-type :NodeDataChanged)
    (let [deployment-parameter (-> (db/retrieve id {})
                                   (add-value-deployment-parameter :watcher (partial persistent-watch-fn event-ch id name)))]
      (sse/send-event id name deployment-parameter event-ch))))

(defn send-event-and-set-watcher
  [event-ch watch-fn {id :id name :name :as deployment-parameter}]
  (let [deployment-parameter (-> deployment-parameter
                                 (add-value-deployment-parameter :watcher (partial watch-fn event-ch id name)))]
    (sse/send-event id name deployment-parameter event-ch)))

(defn retrieve-deployment-parameter
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str (u/de-camelcase resource-name) "/" uuid)
        (db/retrieve request)
        (a/can-view? request)
        (crud/set-operations request))
    (catch ExceptionInfo ei
      (ex-data ei))))

(def retrieve-sse-impl
  (sse/event-channel-handler
    (fn [request response raise event-ch]
      (let [{id :id name :name :as deployment-parameter} (retrieve-deployment-parameter request)
            node-path (zdu/deployment-parameter-znode-path deployment-parameter)]
        (send-event-and-set-watcher event-ch transiant-watch-fn deployment-parameter)))
    {:on-client-disconnect #(log/debug "sse/on-client-disconnect: " %)}))

(def retrieve-json-impl (std-crud/retrieve-fn resource-name))

(defmethod crud/retrieve resource-name
  [{{accept :accept} :headers :as request}]
  (case accept
    "text/event-stream" retrieve-sse-impl
    retrieve-json-impl))

(defn edit-impl
  [{{uuid :uuid} :params body :body :as request}]
  (let [current (-> (str (u/de-camelcase resource-name) "/" uuid)
                    (db/retrieve request)
                    (a/can-modify? request))
        merged (merge current body)
        value (:value merged)]
    (-> merged
        (u/update-timestamps)
        (crud/validate)
        (db/edit request))
    (when value
      (uzk/set-data (zdu/deployment-parameter-znode-path merged) value)))) ;TODO what if znode not found

(defmethod crud/edit resource-name
  [request]
  (edit-impl request))

(def delete-impl (std-crud/delete-fn resource-name))

(defmethod crud/delete resource-name
  [request]
  (delete-impl request))

(def query-sse-impl
  (sse/event-channel-handler
    (fn [request response raise event-ch]
      (a/can-view? {:acl collection-acl} request)
      (let [options (select-keys request [:identity :query-params :cimi-params :user-name :user-roles])
            [count-before-pagination entries] (db/query resource-name options)]
        (doall (map (partial send-event-and-set-watcher event-ch persistent-watch-fn) entries))))
    {:on-client-disconnect #(log/debug "sse/on-client-disconnect: " %) :heartbeat-delay 10}))

(def query-json-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))

(defmethod crud/query resource-name
  [{{accept :accept} :headers :as request}]
  (case accept
    "text/event-stream" query-sse-impl
    query-json-impl))
