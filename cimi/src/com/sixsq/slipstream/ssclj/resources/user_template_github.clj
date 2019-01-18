(ns com.sixsq.slipstream.ssclj.resources.user-template-github
  "
Resource that is used to create a user account from a GitHub authentication
workflow.
"
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as md]
    [com.sixsq.slipstream.ssclj.resources.spec.user-template-github :as utc]
    [com.sixsq.slipstream.ssclj.resources.user-template :as p]
    [com.sixsq.slipstream.ssclj.util.metadata :as gen-md]))


(def ^:const registration-method "github")


(def ^:const resource-name "GitHub")


(def ^:const resource-url registration-method)


(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "ANON"
                            :type      "ROLE"
                            :right     "VIEW"}]})

;;
;; resource
;;

(def ^:const resource
  {:method           registration-method
   :instance         registration-method
   :name             "GitHub Registration"
   :description      "Creates a new user through github-registration"
   :resourceMetadata (str p/resource-url "-" registration-method)
   :acl              resource-acl})


;;
;; initialization: register this User template
;;

(defn initialize
  []
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::utc/github-registration)))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::utc/github-registration))

(defmethod p/validate-subtype registration-method
  [resource]
  (validate-fn resource))
