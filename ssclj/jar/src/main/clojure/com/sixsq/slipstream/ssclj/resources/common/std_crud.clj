(ns com.sixsq.slipstream.ssclj.resources.common.std-crud
  "Standard CRUD functions for resources."
  (:require
    [clojure.walk :as w]
    [clojure.pprint :refer [pprint]]
    [clojure.tools.logging :as log]

    [com.sixsq.slipstream.ssclj.resources.common.authz :as a]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.db.impl :as db]

    [com.sixsq.slipstream.ssclj.resources.common.cimi-filter :as cf]
    [com.sixsq.slipstream.ssclj.resources.common.pagination :as pg]
    [com.sixsq.slipstream.ssclj.resources.common.debug-utils :as du]
    ))

(defn add-fn
  [resource-name collection-acl resource-uri]
  (fn [{:keys [body] :as request}]
    (a/can-modify? {:acl collection-acl} request)
    (db/add
      resource-name
      (-> body
          u/strip-service-attrs
          (crud/new-identifier resource-name)
          (assoc :resourceURI resource-uri)
          u/update-timestamps
          (crud/add-acl request)
          crud/validate))))

(defn retrieve-fn
  [resource-name]
  (fn [{{uuid :uuid} :params :as request}]
    (-> (str (u/de-camelcase resource-name) "/" uuid)
        db/retrieve
        (a/can-view? request)
        (crud/set-operations request)
        (u/json-response))))

(defn edit-fn
  [resource-name]
  (fn [{{uuid :uuid} :params body :body :as request}]
    (let [current (-> (str (u/de-camelcase resource-name) "/" uuid)
                      (db/retrieve)
                      (a/can-modify? request))]
      (->> body
           (u/strip-service-attrs)
           (merge current)
           (u/update-timestamps)
           (crud/validate)
           (db/edit)
           (u/json-response)))))

(defn delete-fn
  [resource-name]
  (fn [{{uuid :uuid} :params :as request}]
    (-> (str (u/de-camelcase resource-name) "/" uuid)
        (db/retrieve)
        (a/can-modify? request)
        (db/delete))))

(defn- paginate-post-sql
  [request entries]
  (pg/paginate (get-in request [:cimi-params :first])
               (get-in request [:cimi-params :last])
               entries))

(defn collection-wrapper-fn
  [resource-name collection-acl collection-uri collection-key]
  (fn [request entries]
    (let [skeleton {:acl         collection-acl
                    :resourceURI collection-uri
                    :id          (u/de-camelcase resource-name)}
          paginated-entries (->> entries
                                 (paginate-post-sql request)
                                 (map #(crud/set-operations % request)))]
      (-> skeleton
          (crud/set-operations request)
          (assoc :count (count entries))
          (assoc collection-key paginated-entries)))))

(defn query-fn
  [resource-name collection-acl collection-uri collection-key]
  (let [wrapper-fn (collection-wrapper-fn resource-name collection-acl collection-uri collection-key)]
    (fn [request]

      (a/can-view? {:acl collection-acl} request)

      (->> (select-keys request [:identity :query-params :cimi-params])

           (db/query resource-name)

           ;; filtering
           (cf/cimi-filter-tree (get-in request [:cimi-params :filter]))

           ;; ordering
           (crud/sort-collection request)

           ;; access controlling
           (filter #(a/authorized-view? % request))

           ;; pagination and inclusion in skeleton
           (wrapper-fn request)

           (u/json-response)))))

(defn resolve-href
  "Pulls in the resource identified by the value of the :href key
   and merges that resource with argument.  Keys specified directly
   in the argument take precedence.  Common attributes in the referenced
   resource are stripped. If :href doesn't exist the argument is
   returned unchanged."
  [{:keys [href] :as resource}]
  (if href
    (-> (db/retrieve href)
        (u/strip-common-attrs)
        (merge resource)
        (dissoc :href))
    resource))

(defn resolve-hrefs
  "Does a prewalk of the given argument, replacing any map with an :href
   attribute with the result of merging the referenced resource (see the
   resolve-href function)."
  [resource]
  (w/prewalk resolve-href resource))
