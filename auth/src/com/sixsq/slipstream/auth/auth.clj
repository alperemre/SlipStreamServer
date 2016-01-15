(ns com.sixsq.slipstream.auth.auth
  (:refer-clojure :exclude [update])
  (:require
    [clojure.data.json :as json]
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.auth.internal-authentication :as ia]
    [com.sixsq.slipstream.auth.github :as gh]
    [com.sixsq.slipstream.auth.utils.http :as uh]))

(defn- extract-claims-token
  [request]
  (-> request
      (uh/select-in-params [:claims :token])
      (update-in [:claims] #(json/read-str % :key-fn keyword))))

(defn dispatch-on-authn-method
  [request]
  (-> request
      (uh/param-value :authn-method)
      keyword))

(defmulti login   dispatch-on-authn-method)

(defmethod login :internal
  [request]
  (ia/login request))

(defmethod login :github
  [_]
  (gh/login))

(defn logout
  [_]
  (ia/logout))

(defn build-token
  [request]
  (log/info "Will build-token")
  (let [{:keys [claims token]}  (extract-claims-token request)
        [ok? token]            (ia/create-token claims token)]
    (if ok?
      (uh/response-with-body 200 (:token token))
      (uh/response-forbidden))))