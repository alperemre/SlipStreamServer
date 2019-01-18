(ns com.sixsq.slipstream.ssclj.resources.configuration-template-session-mitreid
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.configuration-template :as p]
    [com.sixsq.slipstream.ssclj.resources.spec.configuration-template-session-mitreid :as ct-mitreid]))


(def ^:const service "session-mitreid")

;;
;; resource
;;

(def ^:const resource
  {:service        service
   :name           "MITREid Authentication Configuration"
   :description    "MITREid OpenID Connect Authentication Configuration"
   :instance       "authn-name"
   :authorizeURL   "http://auth.example.com"
   :tokenURL       "http://token.example.com"
   :userProfileURL "http://userinfo.example.com"
   :clientID       "server-assigned-client-id"
   :clientSecret   "aaabbbcccdddd"
   :publicKey      "ABCDEF..."})


;;
;; initialization: register this Configuration template
;;
(defn initialize
  []
  (p/register resource))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ct-mitreid/session-mitreid))
(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))
