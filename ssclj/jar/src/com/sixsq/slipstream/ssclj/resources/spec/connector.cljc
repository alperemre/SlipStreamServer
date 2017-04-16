(ns com.sixsq.slipstream.ssclj.resources.spec.connector
  (:require
    [clojure.spec :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.connector-template]))

;;
;; Note that all of the keys and keys specs are already defined
;; with the ConnectorTemplate.  This file exists only to allow a
;; place to define new keys, if that should become necessary.
;;
;; As for the ConnectorTemplate, this is a "base class" so there
;; is no sense in defining map resources for the resource itself.
;;
