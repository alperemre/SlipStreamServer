(ns com.sixsq.slipstream.ssclj.util.zookeeper-utils
  (:require
    [environ.core :as env]
    [clojure.tools.logging :as log]
    [zookeeper :as zk]))

(def ^:dynamic *client*)

(defn set-client!
  [client]
  (alter-var-root #'*client* (constantly client)))

(defn create-client
  "Creates a client connecting to an instance of Zookeeper
  Parameters (host and port) are taken from environment variables."
  []
  (let [zk-endpoints (or (env/env :zk-endpoints) "localhost:2181")]

    (log/info "creating zookeeper client:" zk-endpoints)
    (zk/connect zk-endpoints)))
