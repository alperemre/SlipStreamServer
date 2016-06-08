(ns com.sixsq.slipstream.auth.auth
  (:refer-clojure :exclude [update])
  (:require
    [clojure.data.json :as json]
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.auth.internal :as ia]
    [com.sixsq.slipstream.auth.github :as gh]
    [com.sixsq.slipstream.auth.cyclone :as cy]
    [com.sixsq.slipstream.auth.utils.http :as uh]))

(defn dispatch-on-authn-method
  [request]
  (-> request
      (uh/param-value :authn-method)
      keyword
      (or :internal)))

(defmulti login dispatch-on-authn-method)

(defmethod login :internal
  [request]
  (ia/login request))

(defmethod login :github
  [_]
  (gh/login))

(defmethod login :cyclone
  [_]
  (cy/login))

(defn logout
  [_]
  (ia/logout))

(defn- extract-claims-token
  [request]
  (-> request
      (uh/select-in-params [:claims :token])
      (update-in [:claims] #(json/read-str % :key-fn keyword))))

(defn build-token
  [request]
  (let [{:keys [claims token]} (extract-claims-token request)
        [ok? token] (ia/create-token claims token)]
    (log/debug "token creation status: " ok?)
    (if ok?
      (uh/response-with-body 200 (:token token))
      (uh/response-forbidden))))
