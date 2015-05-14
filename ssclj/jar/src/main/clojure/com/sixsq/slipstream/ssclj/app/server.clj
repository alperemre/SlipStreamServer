(ns com.sixsq.slipstream.ssclj.app.server
  (:require
    [clojure.tools.logging                                          :as log]
    [compojure.handler                                              :as handler]
    [ring.middleware.json                                           :refer [wrap-json-body wrap-json-response]]
    [ring.middleware.params                                         :refer [wrap-params]]
    [metrics.core                                                   :refer [default-registry]]
    [metrics.ring.instrument                                        :refer [instrument]]
    [metrics.ring.expose                                            :refer [expose-metrics-as-json]]
    [metrics.jvm.core                                               :refer [instrument-jvm]]
    [org.httpkit.server                                             :refer [run-server]]
    [com.sixsq.slipstream.ssclj.middleware.base-uri                 :refer [wrap-base-uri]]
    [com.sixsq.slipstream.ssclj.middleware.exception-handler        :refer [wrap-exceptions]]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header        :refer [wrap-authn-info-header]]
    [com.sixsq.slipstream.ssclj.app.routes                          :as routes]
    [com.sixsq.slipstream.ssclj.resources.root                      :as root]
    [com.sixsq.slipstream.ssclj.db.impl                             :as db]
    [com.sixsq.slipstream.ssclj.db.filesystem-binding               :as fsdb]
    [com.sixsq.slipstream.ssclj.db.database-binding                 :as dbdb]))

;; FIXME: make this dynamic depending on the service configuration
(defn set-db-impl
  []  
  ; (-> (fsdb/get-instance fsdb/default-db-prefix)
  ;     (db/set-impl!)))
  (db/set-impl! (dbdb/get-instance)))

(defn create-root
  []
  (try
    (root/add)
    (log/info "created Root resource")
    (catch Exception e
      (log/info "Root resource not created; may already exist; message: " (str e)))))

(defn- create-ring-handler
  "Creates a ring handler that wraps all of the service routes
   in the necessary ring middleware to handle authentication,
   header treatment, and message formatting."
  []
  (log/info "creating ring handler")

  (instrument-jvm default-registry)

  (-> (routes/get-main-routes)
      (handler/site)
      (wrap-exceptions)
      (wrap-base-uri)
      wrap-params
      (wrap-authn-info-header)
      (expose-metrics-as-json "/ssclj/metrics" default-registry {:pretty-print? true})
      (wrap-json-body {:keywords? true})
      (wrap-json-response {:pretty true :escape-non-ascii true})
      (instrument default-registry)))

(defn- start-container
  "Starts the http-kit container with the given ring application and
   on the given port.  Returns the function to be called to shutdown
   the http-kit container."
  [ring-app port]
  (log/info "starting the http-kit container on port" port)
  (run-server ring-app {:port port :ip "127.0.0.1"}))

(declare stop)

(defn- create-shutdown-hook
  [state]
  (proxy [Thread] [] (run [] (stop state))))

(defn register-shutdown-hook
  "This function registers a shutdown hook to close the database
   client cleanly and to shutdown the http-kit container when the
   JVM exits.  This only needs to be called in a context in which
   the stop function will not be explicitly called."
  [state]
  (let [hook (create-shutdown-hook state)]
    (.. (Runtime/getRuntime)
        (addShutdownHook hook))
    (log/info "registered shutdown hook")))

(defn start
  "Starts the server and returns a map with the application
   state containing the function to stop the http-kit container."
  [port]
  (set-db-impl)
  (let [ring-app (create-ring-handler)
        stop-fn (start-container ring-app port)
        state {:stop-fn stop-fn}]
    (create-root)
    state))

(defn stop
  "Stops the http-kit container.  Takes the global state map
   generated by the start function as the argument."
  [{:keys [stop-fn]}]
  (log/info "shutting down http-kit container")
  (if stop-fn
    (stop-fn)))
