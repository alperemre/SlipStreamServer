(ns com.sixsq.slipstream.ssclj.resources.session
  (:require
    [clojure.string :as str]
    [com.sixsq.slipstream.auth.cookies :as cookies]
    [com.sixsq.slipstream.ssclj.resources.session-template :as tpl]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.common.authz :as a]
    [com.sixsq.slipstream.db.impl :as db]
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.filter.parser :as parser]))

(def ^:const resource-tag :sessions)

(def ^:const resource-name "Session")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "SessionCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def ^:const create-uri (str c/slipstream-schema-uri resource-name "Create"))

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "ANON"
                              :type      "ROLE"
                              :right     "MODIFY"}]})

;;
;; validate subclasses of sessions
;;

(defmulti validate-subtype
          :method)

(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown Session type: " (:method resource)) resource)))

(defmethod crud/validate resource-uri
  [resource]
  (validate-subtype resource))

;;
;; validate create requests for subclasses of sessions
;;

(defn dispatch-on-authn-method [resource]
  (get-in resource [:sessionTemplate :method]))

(defmulti create-validate-subtype dispatch-on-authn-method)

(defmethod create-validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown Session create type: " (dispatch-on-authn-method resource) resource) resource)))

(defmethod crud/validate create-uri
  [resource]
  (create-validate-subtype resource))

;;
;; multimethod for ACLs
;;

(defn create-acl
  [id]
  {:owner {:principal id
           :type      "ROLE"}
   :rules [{:principal "ADMIN"
            :type      "ROLE"
            :right     "VIEW"}]})

(defmethod crud/add-acl resource-uri
  [{:keys [id acl] :as resource} request]
  (assoc
    resource
    :acl
    (or acl (create-acl id))))

;;
;; multimethod for adding operations
;; special implementation because
;;   * edit is not permitted
;;   * operations may need to be added for external authn methods
;;

(defmethod crud/set-operations resource-uri
  [{:keys [id resourceURI] :as resource} request]
  (try
    (a/can-modify? resource request)
    (let [ops (if (.endsWith resourceURI "Collection")
                [{:rel (:add c/action-uri) :href id}]
                [{:rel (:delete c/action-uri) :href id}])]
      (if (seq ops)
        (assoc resource :operations ops)
        (dissoc resource :operations)))
    (catch Exception e
      (dissoc resource :operations))))

;;
;; template processing
;;

(defn dispatch-conversion
  [resource _]
  (:method resource))

(defmulti tpl->session dispatch-conversion)

;; default implementation just updates the resourceURI
(defmethod tpl->session :default
  [resource request]
  [nil (assoc resource :resourceURI resource-uri)])

;;
;; CRUD operations
;;

(defn add-impl [{:keys [id body] :as request}]
  (a/can-modify? {:acl collection-acl} request)
  (db/add
    resource-name
    (-> body
        u/strip-service-attrs
        (assoc :id id)
        (assoc :resourceURI resource-uri)
        u/update-timestamps
        (crud/add-acl request)
        crud/validate)
    {}))

;; requires a SessionTemplate to create new Session
(defmethod crud/add resource-name
  [{:keys [body] :as request}]
  (let [idmap {:identity (:identity request)}
        [cookie-header body] (-> body
                                 (assoc :resourceURI create-uri)
                                 (std-crud/resolve-hrefs idmap)
                                 (crud/validate)
                                 (:sessionTemplate)
                                 (tpl->session request))]
    (-> (assoc request :id (:id body) :body body)
        add-impl
        (merge cookie-header))))

(def retrieve-impl (std-crud/retrieve-fn resource-name))

(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))

(def delete-impl (std-crud/delete-fn resource-name))

(defn cookie-name [{:keys [body]}]
  ;; FIXME: The name of the cookie should correspond to the session.
  ;;(str "slipstream." (str/replace (:resource-id body) "/" "."))
  "com.sixsq.slipstream.cookie")

(defn delete-cookie [{:keys [status] :as response}]
  (if (= status 200)
    {:cookies (cookies/revoked-cookie (cookie-name response))}
    {}))

(defmethod crud/delete resource-name
  [request]
  (let [response (delete-impl request)
        cookies (delete-cookie response)]
    (merge response cookies)))

(defn add-session-filter [{{:keys [session]} :sixsq.slipstream.authn/claims :as request}]
  (->> (or session "")
       (format "id='%s'")
       (parser/parse-cimi-filter)
       (assoc-in request [:cimi-params :filter])))

(defn query-wrapper
  "wraps the standard query function to always include a filter based on the session"
  [query-fn]
  (fn [request]
    (query-fn (add-session-filter request))))

(def query-impl (query-wrapper (std-crud/query-fn resource-name collection-acl collection-uri resource-tag)))

(defmethod crud/query resource-name
  [request]
  (query-impl request))
