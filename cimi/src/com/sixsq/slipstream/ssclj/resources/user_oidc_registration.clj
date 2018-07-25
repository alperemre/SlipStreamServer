(ns com.sixsq.slipstream.ssclj.resources.user-oidc-registration
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.email.utils :as email-utils]
    [com.sixsq.slipstream.ssclj.resources.session-oidc.utils :as oidc-utils]
    [com.sixsq.slipstream.ssclj.resources.spec.user]
    [com.sixsq.slipstream.ssclj.resources.spec.user-template-oidc-registration :as user-template-spec]
    [com.sixsq.slipstream.ssclj.resources.user :as p]
    [com.sixsq.slipstream.ssclj.resources.user-template-oidc-registration :as user-template]
    [com.sixsq.slipstream.ssclj.resources.user.utils :as user-utils]))

;;
;; multimethods for validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::user-template-spec/oidc-registration-create))
(defmethod p/create-validate-subtype user-template/registration-method
  [{resource :userTemplate :as create-document}]
  (create-validate-fn create-document))


;;
;; transform template into user resource
;; strips method attribute and updates the resource URI
;;

(def user-defaults {:resourceURI p/resource-uri
                    :isSuperUser false
                    :deleted     false
                    :state       "NEW"})


(defmethod p/tpl->user user-template/registration-method
  [{:keys [href redirectURI] :as resource} {:keys [headers base-uri] :as request}]
  (let [{:keys [clientID authorizeURL]} (oidc-utils/config-oidc-params redirectURI (u/document-id href))
        data (when redirectURI {:redirectURI redirectURI})
        callback-url (user-utils/create-user-oidc-callback base-uri href data)
        redirect-url (oidc-utils/create-redirect-url authorizeURL clientID callback-url)]
        [{:status 303, :headers {"Location" redirect-url}} nil]))