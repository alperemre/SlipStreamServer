(ns com.sixsq.slipstream.ssclj.resources.common.schema
  (:require
    [clojure.tools.logging :as log]
    [clojure.string :as str]
    [schema.core :as s]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u])
  (:import
    [java.net URI]
    [javax.xml.bind DatatypeConverter]
    (com.lambdaworks.codec Base64)))

(def ^:const slipstream-schema-uri "http://sixsq.com/slipstream/1/")

(def ^:const service-context "/ssclj/")

;;
;; actions
;;

(def ^:const valid-actions
  #{:add :edit :delete
    :start :stop :restart :pause :suspend
    :export :import :capture :snapshot})

(def ^:const action-uri
  (let [root "http://sixsq.com/slipstream/1/Action/"]
    (into {} (map (fn [k] [k (str root (name k))]) valid-actions))))

(def ^:const valid-action-uris
  (vals action-uri))

;;
;; schema definitions for basic types
;;

(def NotEmpty
  (s/pred seq "not-empty?"))

(def PosInt
  (s/both s/Int (s/pred pos? "pos?")))

(def NonNegInt
  (s/both s/Int (s/pred (complement neg?) "not-neg?")))

(def NonBlankString
  (s/both s/Str (s/pred (complement str/blank?) "not-blank?")))

(def NonEmptyStrList
  (s/both [NonBlankString] NotEmpty))

(def Timestamp
  (s/both NonBlankString (s/pred u/valid-timestamp? "valid-timestamp?")))

;;
;; schema definitions for common attributes
;;

(def ResourceLink
  {:href NonBlankString})

(def ResourceLinks
  (s/both [ResourceLink] NotEmpty))

(def Operation
  (merge ResourceLink {:rel NonBlankString}))

(def Operations
  (s/both [Operation] NotEmpty))

(def Properties
  (s/both
    {(s/either s/Keyword s/Str) s/Str}
    NotEmpty))

;;
;; Common Attributes
;;

;;
;; These attributes are common to all resources except the
;; CloudEntryPoint.  When these attributes are passed into the
;; CIMI service implementation, the required entries and the
;; :operations will be replaced by the service-generated values.
;;
(def CommonAttrs
  {:id                           NonBlankString
   :resourceURI                  NonBlankString
   (s/optional-key :name)        NonBlankString
   (s/optional-key :description) NonBlankString
   :created                      Timestamp
   :updated                      Timestamp
   (s/optional-key :properties)  Properties
   (s/optional-key :operations)  Operations})

;;
;; These are the common attributes for create resources.
;; All of the common attributes are allowed, but the optional
;; ones other than :name and :description will be ignored.
;;
(def CreateAttrs
  {:resourceURI                  NonBlankString
   (s/optional-key :name)        NonBlankString
   (s/optional-key :description) NonBlankString
   (s/optional-key :created)     Timestamp
   (s/optional-key :updated)     Timestamp
   (s/optional-key :properties)  Properties
   (s/optional-key :operations)  Operations})

;;
;; Ownership and access control
;;
;; These are additions to the standard CIMI schema for the
;; StratusLab implementation.
;;

(def access-control-types (s/enum "USER" "ROLE"))

(def access-control-rights (s/enum "ALL" "VIEW" "MODIFY"))

(def AccessControlId
  {:principal NonBlankString
   :type      access-control-types})

(def AccessControlRule
  (merge AccessControlId {:right access-control-rights}))

(def AccessControlRules
  (s/both [AccessControlRule] NotEmpty))

(def AccessControlList
  {:owner                  AccessControlId
   (s/optional-key :rules) AccessControlRules})

(def AclAttr
  {:acl AccessControlList})
