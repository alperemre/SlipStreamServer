(ns com.sixsq.slipstream.ssclj.resources.external-object
  (:require
    [buddy.core.codecs :as co]
    [buddy.core.hash :as ha]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.auth.acl :as a]
    [com.sixsq.slipstream.db.impl :as db]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.external-object-template :as eot]
    [com.sixsq.slipstream.ssclj.resources.external-object.utils :as s3]
    [com.sixsq.slipstream.ssclj.util.log :as logu]
    [com.sixsq.slipstream.util.response :as r]
    [ring.util.response :as ru]))

(def ^:const resource-tag :externalObjects)

(def ^:const resource-name "ExternalObject")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "ExternalObjectCollection")

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
                              :right     "MODIFY"}]})


(def ^:const state-new "new")
(def ^:const state-uploading "uploading")
(def ^:const state-ready "ready")



;;
;; validate subclasses of externalObject
;;

(defmulti validate-subtype
          :objectType)


(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown External object type: '" (:objectType resource) "'") resource)))


(defmethod crud/validate resource-uri
  [resource]
  (validate-subtype resource))

;;
;; validate create requests for subclasses of external objects
;;

(defn dispatch-on-object-type [resource]
  (get-in resource [:externalObjectTemplate :objectType]))


(defmulti create-validate-subtype dispatch-on-object-type)


(defmethod create-validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown External Object create type: " (dispatch-on-object-type resource) resource) resource)))


(defmethod crud/validate create-uri
  [resource]
  (create-validate-subtype resource))

;;
;; multimethods for validation for the external objects
;;

(defmulti validate-subtype
          "Validates the given resource against the specific
           ExternalObjectTemplate subtype schema."
          :objectType)


(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown ExternalObjectTemplate type: " (:objectType resource)) resource)))


(defmethod crud/validate
  resource-uri
  [resource]
  (validate-subtype resource))

;;
;; multimethod for ACLs
;;

(defn create-acl [id]
  {:owner {:principal "ADMIN"
           :type      "ROLE"}
   :rules [{:principal id
            :type      "USER"
            :right     "MODIFY"}]})


(defmethod crud/add-acl resource-uri
  [resource request]
  (a/add-acl resource request))


(defmethod crud/add-acl resource-uri
  [{:keys [acl] :as resource} request]
  (if acl
    resource
    (let [user-id (:identity (a/current-authentication request))]
      (assoc resource :acl (create-acl user-id)))))


(defn standard-external-object-collection-operations
  [{:keys [id] :as resource} request]
  (when (a/authorized-modify? resource request)
    [{:rel (:add c/action-uri) :href id}]))


(defn standard-external-object-resource-operations
  [{:keys [id state] :as resource} request]
  (let [viewable? (a/authorized-view? resource request)
        modifiable? (a/authorized-modify? resource request)
        show-upload-op? (and modifiable? (#{state-new state-uploading} state))
        show-ready-op? (and modifiable? (#{state-uploading} state))
        show-download-op? (and viewable? (#{state-ready} state))
        ops (cond-> []
                    modifiable? (conj {:rel (:delete c/action-uri) :href id})
                    modifiable? (conj {:rel (:edit c/action-uri) :href id})
                    show-upload-op? (conj {:rel (:upload c/action-uri) :href (str id "/upload")})
                    show-ready-op? (conj {:rel (:ready c/action-uri) :href (str id "/ready")})
                    show-download-op? (conj {:rel (:download c/action-uri) :href (str id "/download")}))]
    (when (seq ops)
      (vec ops))))


(defn standard-external-object-operations
  "Provides a list of the standard external object operations, depending
   on the user's authentication and whether this is a ExternalObject or
   a ExternalObjectCollection."
  [{:keys [resourceURI] :as resource} request]
  (if (.endsWith resourceURI "Collection")
    (standard-external-object-collection-operations resource request)
    (standard-external-object-resource-operations resource request)))


(defmethod crud/set-operations resource-uri
  [resource request]
  (let [ops (standard-external-object-operations resource request)]
    (cond-> (dissoc resource :operations)
            (seq ops) (assoc :operations ops))))


;;
;; Generate ID.
;;

(defmethod crud/new-identifier resource-name
  [{:keys [objectName bucketName] :as resource} resource-name]
  (if-let [new-id (co/bytes->hex (ha/md5 (str objectName bucketName)))]
    (assoc resource :id (str (u/de-camelcase resource-name) "/" new-id))))

;;
;; template processing
;;

(defmulti tpl->externalObject
          "Transforms the ExternalObjectTemplate into a ExternalObject resource."
          :objectType)

;; default implementation just updates the resourceURI

(defmethod tpl->externalObject :default
  [resource]
  (assoc resource :resourceURI resource-uri))

;;
;; CRUD operations
;;

(defn check-cred-exists
  [body idmap]
  (let [href (get-in body [:externalObjectTemplate :objectStoreCred])]
    (std-crud/resolve-hrefs href idmap))
  body)

(defn resolve-hrefs
  [body idmap]
  (let [os-cred-href (if (contains? (:externalObjectTemplate body) :objectStoreCred)
                       {:objectStoreCred (get-in body [:externalObjectTemplate :objectStoreCred])}
                       {})]                                 ;; to put back the unexpanded href after
    (-> body
        (check-cred-exists idmap)
        ;; remove connector href (if any); regular user MAY NOT have rights to see it
        (update-in [:externalObjectTemplate] dissoc :objectStoreCred)
        (std-crud/resolve-hrefs idmap)
        ;; put back unexpanded connector href
        (update-in [:externalObjectTemplate] merge os-cred-href))))


(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))


(defn merge-into-tmpl
  [body]
  (if-let [href (get-in body [:externalObjectTemplate :href])]
    (let [tmpl (-> (get @eot/templates href)
                   u/strip-service-attrs
                   u/strip-common-attrs
                   (dissoc :acl))
          user-resource (-> body
                            :externalObjectTemplate
                            (dissoc :href))]
      (assoc-in body [:externalObjectTemplate] (merge tmpl user-resource)))
    body))

;; requires a ExternalObjectTemplate to create new ExternalObject

(defmethod crud/add resource-name
  [{:keys [body] :as request}]
  (a/can-modify? {:acl collection-acl} request)
  (let [idmap {:identity (:identity request)}
        body (-> body
                 (assoc :resourceURI create-uri)
                 (merge-into-tmpl)
                 (resolve-hrefs idmap)
                 (crud/validate)
                 (:externalObjectTemplate)
                 (tpl->externalObject)
                 (assoc :state state-new))]
    (s3/ok-to-add-external-resource? body request)
    (add-impl (assoc request :body body))))


(def retrieve-impl (std-crud/retrieve-fn resource-name))


(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))


;; editing is special as only a few attributes can be modified

(defn select-editable-attributes
  [body]
  (select-keys body #{:name :description :properties :acl}))

(def edit-impl (std-crud/edit-fn resource-name))


(defmethod crud/edit resource-name
  [{:keys [body] :as request}]
  (edit-impl (assoc request :body (select-editable-attributes body))))


(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))


(defmethod crud/query resource-name
  [request]
  (query-impl request))

;;; Generic utilities for actions

(defn format-states
  [states]
  (->> states
       (map #(format "'%s'" %))
       (str/join ", ")))


(defn error-msg-bad-state
  [action required-states current-state]
  (format "Invalid state '%s' for '%s' action. Valid states: %s."
          current-state action (format-states required-states)))


(defn verify-state
  [{:keys [state] :as resource} accepted-states action]
  (if (accepted-states state)
    resource
    (logu/log-and-throw-400 (error-msg-bad-state action accepted-states state))))


;;; Upload URL operation

(defn upload-fn
  "Provided 'resource' and 'request', returns object storage upload URL.
  It is assumed that the bucket already exists and the user has access to it."
  [{:keys [objectType contentType bucketName objectName objectStoreCred runUUID filename] :as resource} {{ttl :ttl} :body :as request}]
  (verify-state resource #{state-new state-uploading} "upload")
  (let [object-name (if (not-empty objectName)
                      objectName
                      (format "%s/%s" runUUID filename))
        obj-store-conf (s3/format-creds-for-s3-api objectStoreCred)]
    (log/info "Requesting upload url:" object-name)
    (s3/generate-url obj-store-conf bucketName object-name :put
                     {:ttl (or ttl s3/default-ttl) :content-type contentType :filename filename})))


(defn upload
  [resource request]
  (try
    (a/can-modify? resource request)
    (let [upload-uri (upload-fn resource request)]
      (db/edit (assoc resource :state state-uploading) request)
      (r/json-response {:uri upload-uri}))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-url "upload"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-url "/" uuid)]
      (upload (crud/retrieve-by-id-as-admin id) request))
    (catch Exception e
      (or (ex-data e) (throw e)))))



(defmulti ready-subtype
          (fn [resource _] (:objectType resource)))

(defmethod ready-subtype :default
  [resource request]
  (-> resource
      (a/can-modify? request)
      (verify-state #{state-uploading} "ready")
      (assoc :state state-ready)
      (s3/add-s3-size)
      (s3/add-s3-md5sum)
      (db/edit request)))

(defmethod crud/do-action [resource-url "ready"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [resource (crud/retrieve-by-id-as-admin (str resource-url "/" uuid))]
      (ready-subtype resource request))
    (catch Exception e
      (or (ex-data e) (throw e)))))

;;; Download URL operation

(defmulti download-subtype
          "Provided 'resource' and 'request', returns object storage download URL."
          (fn [resource _] (:objectType resource)))



(defmethod download-subtype :default
  [{:keys [bucketName objectName objectStoreCred] :as resource} {{ttl :ttl} :body :as request}]
  (verify-state resource #{state-ready} "download")
  (log/info "Requesting download url: " objectName)
  (s3/generate-url (s3/format-creds-for-s3-api objectStoreCred)
                   bucketName objectName :get
                   {:ttl (or ttl s3/default-ttl)}))


(defn download
  [resource request]
  (let [dl-uri (download-subtype resource request)]
    (try
      (-> {:status  303
           :body    {:uri dl-uri}}
          (ru/header "Location" dl-uri)
          (ru/header "Content-Type" "application/json"))
      (catch Exception e
        (or (ex-data e) (throw e))))))


(defmethod crud/do-action [resource-url "download"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-url "/" uuid)]
      (-> (crud/retrieve-by-id-as-admin id)
          (a/can-view? request)
          (download request)))
    (catch Exception e
      (or (ex-data e) (throw e)))))

;;; Delete resource.

(def delete-impl (std-crud/delete-fn resource-name))


(defn delete
  [{:keys [objectName bucketName objectStoreCred] :as resource}
   {{keep-object? :keep-s3-object, keep-bucket? :keep-s3-bucket} :body :as request}]
  (when-not keep-object?
    (try
      (s3/try-delete-s3-object (s3/format-creds-for-s3-api objectStoreCred) bucketName objectName)
      (log/infof "object %s from bucket %s has been deleted" objectName bucketName)
      (catch Exception e
        ;; When the user requests to delete an S3 object that no longer exists,
        ;; the external object resource should be deleted normally.
        ;; Ignore 404 exceptions.
        (let [status (:status (ex-data e))]
          (when-not (= 404 status)
            (throw e))))))
  ;; Always try to remove the bucket to avoid having unused empty buckets.
  ;; Request will fail when the bucket isn't empty.  These errors are ignored.
  (when-not keep-bucket?
    (try
      (s3/try-delete-s3-bucket (s3/format-creds-for-s3-api objectStoreCred) bucketName)
      (log/debugf "bucket %s became empty and was deleted" bucketName)
      (catch Exception _)))
  (delete-impl request))


(defmethod crud/delete resource-name
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-url "/" uuid)]
      (-> (crud/retrieve-by-id-as-admin id)
          (a/can-modify? request)
          (delete request)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; initialization: no schema for the parent
;;

(defn initialize
  []
  (std-crud/initialize resource-url nil))
