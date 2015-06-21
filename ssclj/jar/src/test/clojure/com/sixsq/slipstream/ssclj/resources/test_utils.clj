(ns com.sixsq.slipstream.ssclj.resources.test-utils
  (:require
    [clojure.string :as s]
    [ring.util.codec :as rc]

    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [ring.middleware.params :refer [wrap-params]]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header wrap-authn-info-header]]
    [com.sixsq.slipstream.ssclj.middleware.base-uri :refer [wrap-base-uri]]
    [com.sixsq.slipstream.ssclj.middleware.cimi-params :refer [wrap-cimi-params]]
    [com.sixsq.slipstream.ssclj.middleware.exception-handler :refer [wrap-exceptions]]
    [com.sixsq.slipstream.ssclj.app.routes :as routes]

    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as t]
    [peridot.core :refer :all]
    [com.sixsq.slipstream.ssclj.resources.common.debug-utils :as du]))

(defn- urlencode-param
  [p]
  (->>  (re-seq #"([^=]*)=(.*)" p)
        first
        next
        (map rc/url-encode)
        (s/join "=")))

(defn urlencode-params
  [query-string]
  (if (empty? query-string)
    query-string
    (let [params (subs query-string 1)]
      (->>  (s/split params #"&")
            (map urlencode-param)
            (s/join "&")
            (str "?")))))

(defn make-ring-app [resource-routes]
  (-> resource-routes
      wrap-base-uri
      wrap-cimi-params
      wrap-params
      wrap-authn-info-header
      wrap-exceptions
      (wrap-json-body {:keywords? true})
      (wrap-json-response {:pretty true :escape-non-ascii true})))

(defn ring-app []
  (make-ring-app (t/concat-routes routes/final-routes)))

(defn exec-request
  ([uri query-string auth-name]
   (-> (session (ring-app))
       (content-type "application/json")
       (header authn-info-header auth-name)
       (request (str uri (urlencode-params query-string))
                :content-type "application/x-www-form-urlencoded")
       (t/body->json))))

(defn is-count
  ([uri expected-count query-string auth-name]
   (-> (exec-request uri query-string auth-name)
       (t/is-status 200)
        (t/is-key-value :count expected-count))))

