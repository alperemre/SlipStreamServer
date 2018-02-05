(ns com.sixsq.slipstream.ssclj.resources.external-object-report
  (:require
    [com.sixsq.slipstream.ssclj.resources.external-object :as eo]
    [com.sixsq.slipstream.ssclj.resources.external-object-template-report :as tpl]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.db.impl :as db]
    [com.sixsq.slipstream.auth.acl :as a]
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.ssclj.util.log :as logu])
  (:import (clojure.lang ExceptionInfo)))

(def ^:const objectType "report")


(def ExternalObjectReportDescription
  tpl/ExternalObjectTemplateReportDescription)

;;
;; description
;;
(def ^:const desc ExternalObjectReportDescription)

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn :cimi/external-object-template.report))
(defmethod eo/validate-subtype objectType
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn :cimi/external-object-template.report-create))
(defmethod eo/create-validate-subtype objectType
  [resource]
  (create-validate-fn resource))

;; Upload URL request operation
(defn upload-fn
  [{state :state id :id :as resource} request]
  (if (= state eo/state-new)
    (do
      (log/warn "Requesting upload url for report  : " id)
      (assoc resource :state eo/state-ready :uploadUri "file://foo"))
    (logu/log-and-throw-400 "Upload url request is not allowed")))

(defmethod eo/upload-subtype objectType
  [resource {{uuid :uuid} :params :as request}]
  (try
    (a/can-modify? resource request)
    (let [id (str (u/de-camelcase eo/resource-name) "/" uuid)]
      (-> (db/retrieve id request)
          (upload-fn request)
          (db/edit request)))
    (catch ExceptionInfo ei
      (ex-data ei))))

;; Download URL request operation
(defn download-fn
  [{state :state id :id :as resource} request]
  (if (= state eo/state-ready)
    (do
      (log/warn "Requesting download url for report : " id)
      (assoc resource :downloadUri "file://foo/bar"))
    (logu/log-and-throw-400 "Getting download  url request is not allowed")))

(defmethod eo/download-subtype objectType
  [resource {{uuid :uuid} :params :as request}]
  (try
    (a/can-modify? resource request)
    (let [id (str (u/de-camelcase eo/resource-name) "/" uuid)]
      (-> (db/retrieve id request)
          (download-fn request)
          (db/edit request)))
    (catch ExceptionInfo ei
      (ex-data ei))))


