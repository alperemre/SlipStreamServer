(ns com.sixsq.slipstream.ssclj.resources.root
  "Root collection for the server providing list of all resource collections."
  (:require
    [clojure.tools.logging :as log]
    [schema.core :as s]
    [compojure.core :refer [defroutes GET POST PUT DELETE ANY]]
    [ring.util.response :as r]
    [com.sixsq.slipstream.ssclj.db.impl :as db]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.common.authz :as a]
    [com.sixsq.slipstream.ssclj.resources.common.dynamic-load :as dyn]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]))

;;
;; utilities
;;

(def ^:const resource-name "Root")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "ANON"
                            :type      "ROLE"
                            :right     "VIEW"}]})

;;
;; Root schema
;;

(def Root
  (merge c/CommonAttrs
         c/AclAttr
         {:baseURI  c/NonBlankString
          s/Keyword c/ResourceLink}))

;; dynamically loads all available resources
(def resource-links
  (into {} (dyn/get-resource-links)))

(def stripped-keys
  (concat (keys resource-links) [:baseURI :operations]))

;;
;; define validation function and add to standard multi-method
;;

(def validate-fn (u/create-validation-fn Root))
(defmethod crud/validate resource-uri
           [resource]
  (validate-fn resource))

(defmethod crud/set-operations resource-uri
           [resource request]
  (try
    (a/can-modify? resource request)
    (let [ops [{:rel (:edit c/action-uri) :href "#"}]]
      (assoc resource :operations ops))
    (catch Exception e
      (dissoc resource :operations))))

;;
;; CRUD operations
;;

(defn add
  "The Root resource is only created automatically at server startup
   if necessary.  It cannot be added through the API.  This function
   adds the minimal Root resource to the database."
  []
  (let [record (-> {:acl         resource-acl
                    :id          resource-name
                    :resourceURI resource-uri}
                   (u/update-timestamps))]
    (db/add record)))

(defn retrieve-impl
  [{:keys [base-uri] :as request}]
  (r/response (-> (db/retrieve resource-name)
                  (a/can-view? request)
                  (assoc :baseURI base-uri)
                  (merge resource-links)
                  (crud/set-operations request))))

(defmethod crud/retrieve resource-name
           [request]
  (retrieve-impl request))

(defn edit-impl
  [{:keys [body] :as request}]
  (let [current (-> (db/retrieve resource-name)
                    (assoc :acl resource-acl)
                    (a/can-modify? request))
        updated (-> body
                    (assoc :baseURI "http://example.org")
                    (u/strip-service-attrs))
        updated (-> (merge current updated)
                    (u/update-timestamps)
                    (merge resource-links)
                    (crud/set-operations request)
                    (crud/validate))]
    (->> (apply dissoc updated stripped-keys)
         (db/edit))
    (r/response updated)))

(defmethod crud/edit resource-name
           [request]
  (edit-impl request))

;;
;; Root doesn't follow the usual /ssclj/ResourceName/UUID
;; pattern, so the routes must be defined explicitly.
;;
(defroutes routes
           (GET c/service-context request
                (crud/retrieve (assoc-in request [:params :resource-name] resource-name)))
           (PUT c/service-context request
                (crud/edit (assoc-in request [:params :resource-name] resource-name)))
           (ANY c/service-context request
                (throw (u/ex-bad-method request))))