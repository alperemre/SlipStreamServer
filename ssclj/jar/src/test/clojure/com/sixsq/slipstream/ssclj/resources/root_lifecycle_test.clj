(ns com.sixsq.slipstream.ssclj.resources.root-lifecycle-test
  (:require
    [com.sixsq.slipstream.ssclj.resources.root :refer :all]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as t]
    [com.sixsq.slipstream.ssclj.db.filesystem-test-utils :as db]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [clojure.test :refer :all]
    [clojure.data.json :as json]
    [peridot.core :refer :all]
    [com.sixsq.slipstream.ssclj.resources.common.schema :as c]))

(use-fixtures :each db/flush-db-fixture)

(use-fixtures :once db/temp-db-fixture)

(def base-uri c/service-context)

(defn ring-app []
  (t/make-ring-app (t/concat-routes [routes])))

(deftest lifecycle

  ;; ensure that database implementation has been bound
  (ring-app)

  ;; initialize the root resource
  (let [response (add)]
    (is 201 (:status response))
    (is resource-name (-> response
                          :headers
                          (get "Location")))
    (is (:body response)))

  ;; retrieve root resource (anonymously should work)
  (-> (session (ring-app))
      (request base-uri)
      (t/body->json)
      (t/is-status 200)
      (t/is-resource-uri resource-uri)
      (t/is-operation-absent "edit")
      (t/is-operation-absent "delete"))

  ;; retrieve root resource (root should have edit rights)
  (-> (session (ring-app))
      (header authn-info-header "root ADMIN")
      (request base-uri)
      (t/body->json)
      (t/is-status 200)
      (t/is-resource-uri resource-uri)
      (t/is-operation-present "edit")
      (t/is-operation-absent "delete"))

  ;; updating root resource as user should fail
  (-> (session (ring-app))
      (content-type "application/json")
      (header authn-info-header "jane")
      (request base-uri
               :request-method :put
               :body (json/write-str {:name "dummy"}))
      (t/body->json)
      (t/is-status 403))

  ;; update the entry, verify updated doc is returned
  ;; must be done as administrator
  (-> (session (ring-app))
      (content-type "application/json")
      (header authn-info-header "root ADMIN")
      (request base-uri
               :request-method :put
               :body (json/write-str {:name "dummy"}))
      (t/body->json)
      (t/is-status 200)
      (t/is-resource-uri resource-uri)
      (t/is-operation-present "edit")
      (t/is-key-value :name "dummy"))

  ;; verify that subsequent reads find the right data
  (-> (session (ring-app))
      (request base-uri)
      (t/body->json)
      (t/is-status 200)
      (t/is-resource-uri resource-uri)
      (t/is-operation-absent "edit")
      (t/is-key-value :name "dummy")))

(deftest bad-methods
  (doall
    (for [[uri method] [[base-uri :options]
                        [base-uri :delete]
                        [base-uri :post]]]
      (do
        (-> (session (ring-app))
            (request uri
                     :request-method method
                     :body (json/write-str {:dummy "value"}))
            (t/is-status 405))))))