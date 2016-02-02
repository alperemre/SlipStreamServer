(ns com.sixsq.slipstream.ssclj.middleware.proxy-redirect
  (:require
    [clojure.tools.logging :as log]
    [com.sixsq.slipstream.ssclj.middleware.logger :as ml]
    [superstring.core :as str]

    ;; [puppetlabs.http.client.sync :as http]
    ;; [clj-http.client :as http]
    [aleph.http :as http]

    [ring.middleware.cookies :refer [wrap-cookies]]
    [clj-time.core :refer [in-seconds]]
    [clj-time.format :refer [formatters unparse with-locale]]
    [com.sixsq.slipstream.ssclj.middleware.base-uri :as buri]
    [com.sixsq.slipstream.ssclj.util.config :as cf]
    [ring.util.response :as r]
    [clj-stacktrace.repl :as st])
  (:import
    [java.io ByteArrayInputStream]
    [java.io InputStream]
    [java.net URL]
    [java.net URI]))

;; Inspired by : https://github.com/tailrecursion/ring-proxy

(def ^:const location-header-path [:headers "location"])

(defn- uri-starts-with?
  [uri prefixes]
  (some #(.startsWith uri %) prefixes))

(defn- build-url
  [host path query-string]
  (str (.toString (URL. (URL. host) path))
       (if query-string
         (str "?" query-string)
         "")))

(defn slurp-binary
  "Reads len bytes from InputStream is and returns a byte array."
  [^InputStream is len]
  (with-open [rdr is]
    (let [buf (byte-array len)]
      (.read rdr buf)
      buf)))

(defn- content-length
  [request]
  (get-in request [:headers "content-length"]))

(defn slurp-body-binary
  [request]
  (when-let [len (and (:body request) (content-length request))]
    (-> request
        :body
        (slurp-binary (Integer/parseInt len))
        ByteArrayInputStream.)))

(defn- split-equals
  [key-val]
  (let [index-equals (.indexOf key-val "=")
        len (count key-val)]
    (if (> index-equals -1)
      [(.substring key-val 0 index-equals)
       (.substring key-val (inc index-equals) len)]
      [])))

(defn to-query-params
  [query-string]
  (if (nil? query-string)
    {}
    (let [kvs (str/split query-string #"&")]
      (into {} (remove empty? (map split-equals kvs))))))

(defn strip-leading-slashes
  [s]
  (second (re-matches #"^(?:/*)?(.*)$" s)))

(defn strip-trailing-slashes
  [s]
  (second (re-matches #"^(.*?)(?:/*)?$" s)))

(defn- external?
  "True when host of location is not the upstream server"
  [location]
  (not= (.getHost (URI. location))
        (.getHost (URI. (cf/property-value :upstream-server)))))

(defn update-location
  [location base-uri]
  (if (external? location)
    location
    (let [uri (URI. location)
          path (or (.getRawPath uri) "")
          query (.getRawQuery uri)
          fragment (.getRawFragment uri)]
      (str (strip-trailing-slashes base-uri)
           "/"
           (if path (strip-leading-slashes path) "")
           (if query (str "?" query) "")
           (if fragment (str "#" fragment) "")))))

(defn update-location-header
  [response base-uri]
  (if (get-in response location-header-path)
    (update-in response location-header-path #(update-location % base-uri))
    response))

;; NOTE: this method uses the synchronous calls for the http client.  The persistent
;; asynchronous client appears to either be caching credentials (allowing inappropriate
;; reuse by different users) or not to be thread-safe.
(defn- redirect
  [host request-uri request]

  (let [redirected-url  (build-url host request-uri (:query-string request))

        request-fn (case (:request-method request)
                     :get     http/get
                     :head    http/head
                     :post    http/post
                     :delete  http/delete
                     :put     http/put)

        forwarded-headers (-> request
                              :headers
                              (dissoc "content-length"))

        query-params      (merge (to-query-params (:query-string request)) (:params request))

        response (request-fn redirected-url
                             {:query-params                 query-params
                              :body                         (slurp-body-binary request)
                              :headers                      forwarded-headers

                              :connection-timeout           60000
                              :request-timeout              60000
                              :follow-redirects?            false
                              :pool-timeout                 60000

                              ; puppet-labs configuration
                              ;:force-redirects              false
                              ;:follow-redirects             false
                              ;:connect-timeout-milliseconds 60000
                              ;:socket-timeout-milliseconds  60000
                              })

        location-updated-response (update-location-header @response (buri/construct-base-uri request "/"))]

    (ml/log-request-response request location-updated-response)

    location-updated-response))


(defn- error-message
  [exception]
  (str  "Error when contacting upstream server. SlipStream server may be stopped. Please contact administrator.\n"
        "Detailed error message:\n"
        (.getMessage exception)))

(defn- error-response
  [exception]
  (-> exception
      error-message
      r/response
      (r/status 500)))

(defn wrap-proxy-redirect
  [handler except-uris host]
  (fn [request]
    (let [request-uri (:uri request)]
      (if (uri-starts-with? request-uri except-uris)
        (handler request)
        (try
          (redirect host request-uri request)
          (catch Exception e
            (if-let [response-in-ex (ex-data e)]
              ;; FIXME work-around to aleph sending exception instead of regular responses
              response-in-ex
              (do
                (log/error (st/pst-str e))
                (error-response e)))))))))
