(ns com.sixsq.slipstream.ssclj.resources.external-object-template-public
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.external-object :as eo]
    [com.sixsq.slipstream.ssclj.resources.external-object-template :as eot]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object-public :as eo-public]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object-template-public :as eot-public]))


(def ^:const objectType "public")


;;
;; resource
;;

(def ^:const resource
  {:objectType      objectType
   :contentType     "content/type"
   :objectStoreCred {:href "credential/cloud-cred"}
   :bucketName      "bucket-name"
   :objectName      "object/name"})


;;
;; initialization: register this external object generic template
;;

(defn initialize
  []
  (eot/register resource))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::eo-public/external-object))
(defmethod eo/validate-subtype objectType
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::eot-public/external-object-create))
(defmethod eo/create-validate-subtype objectType
  [resource]
  (create-validate-fn resource))


(def validate-fn (u/create-spec-validation-fn ::eot-public/externalObjectTemplate))
(defmethod eot/validate-subtype-template objectType
  [resource]
  (validate-fn resource))