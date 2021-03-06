(ns com.sixsq.slipstream.ssclj.resources.configuration-template-session-mitreid-token
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.configuration-template :as p]
    [com.sixsq.slipstream.ssclj.resources.spec.configuration-template-session-mitreid-token :as cts-mitreid-token]))


(def ^:const service "session-mitreid-token")


;;
;; resource
;;

(def ^:const resource
  {:service     service
   :name        "OIDC Token Authentication Configuration"
   :description "OpenID Connect Token Authentication Configuration"
   :instance    "authn-name"
   :clientIPs   ["127.0.0.1"]})


;;
;; description
;;

(def ^:const desc
  (merge p/ConfigurationTemplateDescription
         {:clientIPs {:displayName "Authorized Client IPs"
                      :type        "string"
                      :description "list of authorized client IP address for OIDC token authentication"
                      :mandatory   true
                      :readOnly    false
                      :order       22}}))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cts-mitreid-token/schema))
(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Configuration template
;;

(defn initialize
  []
  (p/register resource desc))
