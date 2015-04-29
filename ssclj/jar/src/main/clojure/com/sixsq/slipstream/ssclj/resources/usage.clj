(ns 
  com.sixsq.slipstream.ssclj.resources.usage
  (:require

    [clojure.tools.logging                                      :as log]
    [clojure.java.jdbc                                          :as j]

    [schema.core                                                :as s]    

    [korma.core                                                 :refer :all]

    [honeysql.core                                              :as sql]
    [honeysql.helpers                                           :as hh]

    [com.sixsq.slipstream.ssclj.usage.record-keeper             :as rc]
    [com.sixsq.slipstream.ssclj.database.korma-helper           :as kh]
    [com.sixsq.slipstream.ssclj.db.database-binding             :as dbb]    
    [com.sixsq.slipstream.ssclj.resources.common.authz          :as a]
    [com.sixsq.slipstream.ssclj.resources.common.crud           :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud       :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils          :as u]
    [com.sixsq.slipstream.ssclj.db.filesystem-binding-utils     :as fu]
    [com.sixsq.slipstream.ssclj.resources.common.debug-utils    :as du]
    [com.sixsq.slipstream.ssclj.resources.common.schema         :as c]))

(def ^:const resource-tag     :usages)
(def ^:const resource-name    "Usage")
(def ^:const collection-name  "UsageCollection")

(def ^:const resource-uri     (str c/slipstream-schema-uri resource-name))
(def ^:const collection-uri   (str c/slipstream-schema-uri collection-name))

(def ^:const pagination-limit-max     "500")
(def ^:const pagination-limit-default "20")

(def collection-acl {:owner {:principal   "ADMIN"
                             :type        "ROLE"}

                     :rules [{:principal  "ANON"
                              :type       "ROLE"
                              :right      "VIEW"}]})

(defonce init-record-keeper (rc/-init))

(defentity usage_summaries)
(defentity acl)

(defn- deserialize-usage   
  [usage]
  (update-in usage [:acl] fu/deserialize))

(defn offset-limit 
  [options]
  (->> options 
       :query-params      
       (merge {"offset" "0" "limit" pagination-limit-default})
       ((juxt #(get % "offset") #(get % "limit")))))

(defn id-roles   
  [options]
  (-> options            
      :identity      
      :authentications   
      (get (get-in options [:identity :current]))
      ((juxt :identity :roles))))

(defn id-matches?   
  [id]
  (if id
    [:and [:= :a.principal-type "USER"] [:= :a.principal-name id]]
    [:= 0 1]))

(defn roles-in?   
  [roles]
  (if (seq roles)
    [:and [:= :a.principal-type "ROLE"] [:in :a.principal-name roles]]
    [:= 0 1]))

(defn- neither-id-roles?
  [id roles]
  (not (or id (seq roles))))

(defn check-offset-limit
  [offset limit]
  (when (> (Integer. limit) (Integer. pagination-limit-max))
    (throw (u/ex-forbidden resource-name))))

(defn sql   
  [id roles offset limit]
  (check-offset-limit offset limit)
  (->   (hh/select :u.*) 
        (hh/from [:acl :a] [:usage_summaries :u])
        (hh/where [:and [:= :u.id :a.resource-id]
                        [:or 
                          (id-matches? id)
                          (roles-in? roles)]])
        (hh/modifiers :distinct)
        (hh/limit limit)
        (hh/offset offset)
        (hh/order-by [:u.start_timestamp :desc])

        (sql/format :quoting :ansi)))

(defmethod dbb/find-resources resource-name
  [collection-id options]
  (let [[id roles] (id-roles options)
        [offset limit] (offset-limit options)]     
    (if (neither-id-roles? id roles)
      []
      (->> (sql id roles offset limit)
           (j/query kh/db-spec)           
           (map deserialize-usage)))))

;;
;; schemas
;;

(def Usage
  (merge
    c/CreateAttrs
    c/AclAttr
    { 
      :id               c/NonBlankString
      :user             c/NonBlankString
      :cloud            c/NonBlankString
      :start_timestamp  c/Timestamp
      :end_timestamp    c/Timestamp
      :usage            c/NonBlankString
       ;   c/NonBlankString { ;; metric-name
       ;     :cloud_vm_instanceid      c/NonBlankString
       ;     :unit_minutes   c/NonBlankString } 
       ; }
    }))

(def validate-fn (u/create-validation-fn Usage))

;;
;; collection
;;

(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))

(defmethod crud/query resource-name
  [request]      
  (query-impl request))

;;
;; single
;;

(defn- check-exist 
  [resource id]
  (if (empty? resource)
    (throw (u/ex-not-found id))
    resource))

(defn find-resource
  [id]
  (-> (select usage_summaries (where {:id id}) (limit 1))
      (check-exist id)
      first            
      deserialize-usage))

(defn retrieve-fn
  [request]
  (fn [{{uuid :uuid} :params :as request}]
    (-> (str resource-name "/" uuid)        
        find-resource        
        (a/can-view? request)
        (crud/set-operations request)
        (u/json-response))))

(defmethod crud/retrieve resource-name
  [request]
  (retrieve-fn request))
