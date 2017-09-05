(ns com.sixsq.slipstream.ssclj.resources.deployment-lifecycle-test
  (:require
    [clojure.test :refer :all]
    [clojure.data.json :as json]
    [peridot.core :refer :all]
    [ring.util.codec :as rc]
    [com.sixsq.slipstream.ssclj.resources.deployment :refer :all]
    [com.sixsq.slipstream.ssclj.resources.deployment-parameter :as dp]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as t]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.app.routes :as routes]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.util.zookeeper :as uzk]
    [com.sixsq.slipstream.ssclj.resources.zk.deployment.utils :as zdu]))

(use-fixtures :each t/with-test-es-client-fixture)

(use-fixtures :once t/setup-embedded-zk)

(def base-uri (str p/service-context resource-url))

(defn ring-app []
  (t/make-ring-app (t/concat-routes routes/final-routes)))

(def valid-entry-deployment-uuid "dfd34916-6ede-47f7-aaeb-a30ddecbba5c")

(def valid-entry
  {:id                  (str resource-url "/" valid-entry-deployment-uuid)
   :resourceURI         resource-uri
   :module-resource-uri "module/examples/tutorials/service-testing/system/1940"
   :category            "Deployment"
   :type                "Orchestration"
   :mutable             false
   :nodes               {:node1 {:parameters         {:cloudservice {:description       "p1 description" ;TODO only node name and  multiplicity are being used for now from all these parameters
                                                                     :default-value     "abc"
                                                                     :user-choice-value "ABC"}
                                                      :multiplicity {:default-value "1"}}
                                 :runtime-parameters {:p1 {:description       "p1 description"
                                                           :default-value     "abc"
                                                           :user-choice-value "ABC"
                                                           :mapped-to         "a"}}}
                         :node2 {:parameters {:cloudservice {:description   "param1 description"
                                                             :default-value "abc"}}}}})

(deftest create-deployment

  (let [session-admin-json (-> (session (ring-app))
                               (content-type "application/json")
                               (header authn-info-header "super ADMIN USER ANON"))
        session-admin-form (-> (session (ring-app))
                               (content-type "application/x-www-form-urlencoded")
                               (header authn-info-header "super ADMIN USER ANON"))
        session-user (-> (session (ring-app))
                         (content-type "application/json")
                         (header authn-info-header "jane USER ANON"))
        session-anon (-> (session (ring-app))
                         (content-type "application/json"))]

    ;; adding, retrieving and  deleting entry as user should succeed
    (let [deployment-href (-> session-user
                              (request base-uri
                                       :request-method :post
                                       :body (json/write-str valid-entry))
                              (t/body->edn)
                              (t/is-status 201)
                              (t/location))
          abs-uri (str p/service-context (u/de-camelcase deployment-href))
          created-deployment (-> session-user
                                 (request abs-uri)
                                 (t/body->edn)
                                 (t/is-status 200))]

      (is (not (uzk/exists (str zdu/znode-separator deployment-href)))))))

(deftest start-deployment

  (let [session-admin-json (-> (session (ring-app))
                               (content-type "application/json")
                               (header authn-info-header "super ADMIN USER ANON"))
        session-admin-form (-> (session (ring-app))
                               (content-type "application/x-www-form-urlencoded")
                               (header authn-info-header "super ADMIN USER ANON"))
        session-user (-> (session (ring-app))
                         (content-type "application/json")
                         (header authn-info-header "jane USER ANON"))
        session-anon (-> (session (ring-app))
                         (content-type "application/json"))]

    ;; adding, retrieving and  deleting entry as user should succeed
    (let [deployment-href (-> session-user
                              (request base-uri
                                       :request-method :post
                                       :body (json/write-str valid-entry))
                              (t/body->edn)
                              (t/is-status 201)
                              (t/location))
          abs-uri (str p/service-context (u/de-camelcase deployment-href))
          created-deployment (-> session-user
                                 (request abs-uri)
                                 (t/body->edn)
                                 (t/is-status 200))
          start-uri (str p/service-context
                         (t/get-op created-deployment "http://schemas.dmtf.org/cimi/2/action/start"))
          started-deployment (-> session-user
                                 (request start-uri)
                                 (t/body->edn)
                                 (t/is-status 200))
          ]

      (is (not (get-in created-deployment [:body :start-time])))
      (is (get-in started-deployment [:response :body :start-time]))

      (is (= "init" (get-in started-deployment [:response :body :state])))

      (are [expected value]
        (= expected value)
        "init" (uzk/get-data (str zdu/znode-separator deployment-href "/state"))
        "init" (uzk/get-data
                 (str zdu/znode-separator deployment-href "/" zdu/nodes-txt "/node2/1/" "vmstate"))))))


(deftest start-deployment

  (let [session-admin-json (-> (session (ring-app))
                               (content-type "application/json")
                               (header authn-info-header "super ADMIN USER ANON"))
        session-admin-form (-> (session (ring-app))
                               (content-type "application/x-www-form-urlencoded")
                               (header authn-info-header "super ADMIN USER ANON"))
        session-user (-> (session (ring-app))
                         (content-type "application/json")
                         (header authn-info-header "jane USER ANON"))
        session-anon (-> (session (ring-app))
                         (content-type "application/json"))]

    ;; adding, retrieving and  deleting entry as user should succeed
    (let [deployment-href (-> session-user
                              (request base-uri
                                       :request-method :post
                                       :body (json/write-str valid-entry))
                              (t/body->edn)
                              (t/is-status 201)
                              (t/location))
          abs-uri (str p/service-context (u/de-camelcase deployment-href))
          created-deployment (-> session-user
                                 (request abs-uri)
                                 (t/body->edn)
                                 (t/is-status 200))
          start-uri (str p/service-context
                         (t/get-op created-deployment "http://schemas.dmtf.org/cimi/2/action/start"))
          started-deployment (-> session-user
                                 (request start-uri)
                                 (t/body->edn)
                                 (t/is-status 200))
          ]

      (is (not (get-in created-deployment [:body :start-time])))
      (is (get-in started-deployment [:response :body :start-time]))

      (is (= "init" (get-in started-deployment [:response :body :state])))

      (are [expected value]
        (= expected value)
        "init" (uzk/get-data (str zdu/znode-separator deployment-href "/state"))
        "init" (uzk/get-data
                 (str zdu/znode-separator deployment-href "/" zdu/nodes-txt "/node2/1/" "vmstate"))))))
(deftest create-deployment-update-deployment-state

  (let [session-admin-json (-> (session (ring-app))
                               (content-type "application/json")
                               (header authn-info-header "super ADMIN USER ANON"))
        session-user (-> (session (ring-app))
                         (content-type "application/json")
                         (header authn-info-header "jane USER ANON"))]

    ;; adding, retrieving and  deleting entry as user should succeed
    (let [deployment-href (-> session-user
                              (request base-uri
                                       :request-method :post
                                       :body (json/write-str valid-entry))
                              (t/body->edn)
                              (t/is-status 201)
                              (t/location))
          abs-uri (str p/service-context (u/de-camelcase deployment-href))
          abs-uri-deployment-parameter-state (str p/service-context dp/resource-url "/" valid-entry-deployment-uuid "_state")
          created-deployment (-> session-user
                                 (request abs-uri)
                                 (t/body->edn)
                                 (t/is-status 200))
          start-uri (str p/service-context
                         (t/get-op created-deployment "http://schemas.dmtf.org/cimi/2/action/start"))
          started-deployment (-> session-user
                                 (request start-uri)
                                 (t/body->edn)
                                 (t/is-status 200))
          update-deployment-parameter-state
          (-> session-user
              (request abs-uri-deployment-parameter-state :request-method :put
                       :body (json/write-str {:value "provisioning"}))
              (t/body->edn)
              (t/is-status 403))
          update-deployment-parameter-state
          (-> session-admin-json
              (request abs-uri-deployment-parameter-state :request-method :put
                       :body (json/write-str {:value "provisioning"}))
              (t/body->edn)
              (t/is-status 200))
          provisioning-deployment (-> session-user
                                 (request start-uri)
                                 (t/body->edn)
                                 (t/is-status 200))]

      (is (= "provisioning" (get-in provisioning-deployment [:response :body :state])))

      (is (= "provisioning" (uzk/get-data (str zdu/znode-separator deployment-href "/state"))))
      )))