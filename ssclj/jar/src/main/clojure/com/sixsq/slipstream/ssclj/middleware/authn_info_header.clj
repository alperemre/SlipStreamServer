(ns com.sixsq.slipstream.ssclj.middleware.authn-info-header
  (:require
    [superstring.core :as s]
    [com.sixsq.slipstream.auth.sign :as sign]))

;; NOTE: ring uses lowercased values of header names!
(def ^:const authn-info-header
  "slipstream-authn-info")

(def ^:const authn-cookie
  "com.sixsq.slipstream.cookie")

(defn extract-authn-info
  [request]
  (let [terms (remove s/blank? (-> request
                                   (get-in [:headers authn-info-header])
                                   (or "")
                                   (s/split #"\s+")))]
    (when (seq terms)
      ((juxt first rest) terms))))

(defn extract-cookie-info
  [request]
  (try
    (if-let [token (get-in request [:cookies authn-cookie :value])]
      (let [claims (sign/unsign-claims token)
            identifier (:com.sixsq.identifier claims)
            roles (remove s/blank? (-> claims
                                       :com.sixsq.roles
                                       (or "")
                                       (s/split #"\s+")))]
        (when identifier
          [identifier roles])))
    (catch Exception ex
      nil)))

(defn extract-info [request]
  (or
    (extract-authn-info request)
    (extract-cookie-info request)))

(defn create-identity-map
  [[username roles]]
  (if username
    (let [id-map (if (seq roles) {:roles roles} {})
          id-map (assoc id-map :identity username)]
      {:current         username
       :authentications {username id-map}})
    {}))

(defn wrap-authn-info-header
  "Middleware that adds an identity map to the request based on
   information in the slipstream-authn-info header or authentication
   cookie.  If both are provided, the header takes precedence."
  [handler]
  (fn [request]
    (->> request
         (extract-info)
         (create-identity-map)
         (assoc request :identity)
         (handler))))
