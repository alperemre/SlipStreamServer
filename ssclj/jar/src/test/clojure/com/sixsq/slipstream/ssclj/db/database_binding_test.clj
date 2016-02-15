(ns com.sixsq.slipstream.ssclj.db.database-binding-test
  (:refer-clojure :exclude [update])
  (:require
    [com.sixsq.slipstream.ssclj.db.database-binding :as dbb]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as cu]
    [korma.core :refer :all]
    [expectations :refer :all]
    [clojure.tools.logging :as log]))

(def db (dbb/get-instance))

(defentity resources)
(delete resources)
(log/info "All resources deleted")

;; Given a clean database
(def data {:id  "type/123" :name "alfred" :age 23
           :acl {:owner {:type "USER" :principal "alfred"}
                 :rules [{:type "USER" :principal "alfred" :right "ALL"}]}})
(def response-add (.add db "Type" data))
;; When we add data

(expect 201 (:status response-add))
(expect "type/123" (get-in response-add [:headers "Location"]))
(expect "application/json" (get-in response-add [:headers "Content-Type"]))
(expect {:status 201 :message "created type/123" :resource-id "type/123"} (:body response-add))
;; Then the result is a ring response
;; with status, headers and body correctly filled

(def resources-in-db (select resources))
(expect 1 (count resources-in-db))
;; Then one line has been persisted
(def row (first resources-in-db))
(expect "type/123" (:id row))
(expect (cu/serialize data) (:data row))
;; Then id and data (serialized) have been stored

;; this form fails"
;; (expect clojure.lang.ExceptionInfo (.add db data))
;; TODO : something to be done with transaction?
(try
  (.add db "type" data)
  (expect false "should have been caught")
  (catch Exception e
    (log/info "caught exception: class=" (class e))))
;; Then it is not allowed to re-add a data with the same id

(expect clojure.lang.ExceptionInfo (.retrieve db 9999))
; ;; Then an exception is thrown when we try to retrieve with a wrong id

(def retrieved (.retrieve db "type/123"))
;; When we retrieve data
(expect data retrieved)
;; Then it equals what was added

(def collection (.query db "type" {:identity
                                   {:current         "alfred"
                                    :authentications {"alfred" {:identity "alfred" :roles []}}}}))

(expect (list data) collection)
(expect empty? (.query db "unknown" {}))
;; Then collections can be queried
