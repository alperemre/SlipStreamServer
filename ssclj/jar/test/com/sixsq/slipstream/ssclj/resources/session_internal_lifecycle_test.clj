(ns com.sixsq.slipstream.ssclj.resources.session-internal-lifecycle-test
  (:require
    [clojure.test :refer :all]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [peridot.core :refer :all]
    [com.sixsq.slipstream.ssclj.resources.session :as session]
    [com.sixsq.slipstream.ssclj.resources.session-internal :as si]
    [com.sixsq.slipstream.ssclj.resources.session-template :as ct]
    [com.sixsq.slipstream.ssclj.resources.session-template-internal :as internal]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.common.dynamic-load :as dyn]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.auth.internal :as auth-internal]
    [com.sixsq.slipstream.auth.utils.db :as db]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.app.routes :as routes]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.auth.utils.sign :as sign]))

(use-fixtures :each ltu/with-test-client-fixture)

(def base-uri (str p/service-context (u/de-camelcase session/resource-name)))

(defn ring-app []
  (ltu/make-ring-app (ltu/concat-routes [(routes/get-main-routes)])))

;; initialize must to called to pull in SessionTemplate test examples
(dyn/initialize)

(defn strip-unwanted-attrs [m]
  (let [unwanted #{:id :resourceURI :acl :operations
                   :created :updated :name :description}]
    (into {} (remove #(unwanted (first %)) m))))

(defn mock-login-valid?
  "Will return true if the username and password are identical;
   false otherwise.  Avoids having to start a real database and
   populate it with users."
  [{:keys [username password]}]
  (= username password))

(defn mock-roles
  "Mocking function to return the roles for a given user.  For
   'root' the 'ADMIN', 'USER', and 'ANON' roles will be added. F
   For all others, the 'USER' and 'ANON' roles will be added."
  [username]
  (str/join " " (case username
                  "root" ["ADMIN" "USER" "ANON"]
                  ["USER" "ANON"])))

(deftest check-create-claims
  (with-redefs [db/find-roles-for-username mock-roles]
    (let [username "root"
          server "nuv.la"
          headers {:slipstream-ssl-server-name server}
          session-id "session/72e9f3d8-805a-421b-b3df-86f1af294233"
          client-ip "127.0.0.1"]
      (is (= {:username username
              :session  session-id
              :roles    (str/join " " ["ADMIN" "USER" "ANON" session-id])
              :server   server
              :clientIP client-ip}
             (si/create-claims username headers session-id client-ip)))
      (is (= {:username "not-root"
              :roles    (str/join " " ["USER" "ANON"])
              :server   server}
             (si/create-claims "not-root" headers nil nil))))))

(deftest lifecycle

  (with-redefs [auth-internal/valid? mock-login-valid?
                db/find-roles-for-username mock-roles]

    ;; check that the mocking is working correctly
    (is (auth-internal/valid? {:username "user" :password "user"}))
    (is (not (auth-internal/valid? {:username "user" :password "BAD"})))
    (is (= (str/join " " ["ADMIN" "USER" "ANON"]) (db/find-roles-for-username "root")))
    (is (= (str/join " " ["USER" "ANON"]) (db/find-roles-for-username "user")))

    ;; get session template so that session resources can be tested
    (let [session-anon (-> (session (ring-app))
                           (content-type "application/json")
                           (header authn-info-header "unknown ANON"))

          session-user (-> (session (ring-app))
                           (content-type "application/json")
                           (header authn-info-header "user USER"))

          session-admin (-> (session (ring-app))
                            (content-type "application/json")
                            (header authn-info-header "root ADMIN"))

          href (str ct/resource-url "/" internal/authn-method)
          template-url (str p/service-context ct/resource-url "/" internal/authn-method)
          resp (-> session-anon
                   (request template-url)
                   (ltu/body->edn)
                   (ltu/is-status 200))
          template (get-in resp [:response :body])
          valid-create {:sessionTemplate (strip-unwanted-attrs (assoc template :username "user" :password "user"))}
          href-create {:sessionTemplate {:href     href
                                         :username "user"
                                         :password "user"}}
          unauthorized-create (update-in href-create [:sessionTemplate :password] (constantly "BAD"))
          invalid-create (assoc-in valid-create [:sessionTemplate :invalid] "BAD")]

      ;; anonymous query should succeed but have no entries
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?))

      ;; unauthorized create must return a 403 response
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str unauthorized-create))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; anonymous create must succeed (normal create and href create)
      (let [resp (-> session-anon
                     (request base-uri
                              :request-method :post
                              :body (json/write-str valid-create))
                     (ltu/is-set-cookie)
                     (ltu/body->edn)
                     (ltu/is-status 201))
            id (get-in resp [:response :body :resource-id])

            token (get-in resp [:response :cookies "com.sixsq.slipstream.cookie" :value :token])
            claims (if token (sign/unsign-claims token) {})

            uri (-> resp
                    (ltu/location))
            abs-uri (str p/service-context (u/de-camelcase uri))

            resp (-> session-anon
                     (request base-uri
                              :request-method :post
                              :body (json/write-str href-create))
                     (ltu/is-set-cookie)
                     (ltu/body->edn)
                     (ltu/is-status 201))
            id2 (get-in resp [:response :body :resource-id])
            uri2 (-> resp
                     (ltu/location))
            abs-uri2 (str p/service-context (u/de-camelcase uri2))]

        ;; check claims in cookie
        (is (= "user" (:username claims)))
        (is (= (str/join " " ["USER" "ANON" uri]) (:roles claims))) ;; uri is also session id
        (is (= uri (:session claims)))                      ;; uri is also session id
        (is (not (nil? (:exp claims))))

        ;; user should not be able to see session without session role
        (-> session-user
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 403))
        (-> session-user
            (request abs-uri2)
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; anonymous query should succeed but still have no entries
        (-> session-anon
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?))

        ;; user query should succeed but have no entries because of missing session role
        (-> session-user
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?))

        ;; admin query should succeed, but see no sessions without the correct session role
        (-> session-admin
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 0))

        ;; user should be able to see session with session role
        (-> (session (ring-app))
            (header authn-info-header (str "user USER " id))
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-id id)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-absent "edit"))
        (-> (session (ring-app))
            (header authn-info-header (str "user USER " id2))
            (request abs-uri2)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-id id2)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-absent "edit"))

        ;; user query with session role should succeed but and have one entry
        (-> (session (ring-app))
            (header authn-info-header (str "user USER " id))
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 1))
        (-> (session (ring-app))
            (header authn-info-header (str "user USER " id2))
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 1))

        ;; user with session role can delete resource
        (-> (session (ring-app))
            (header authn-info-header (str "user USER " id))
            (request abs-uri
                     :request-method :delete)
            (ltu/is-unset-cookie)
            (ltu/body->edn)
            (ltu/is-status 200))
        (-> (session (ring-app))
            (header authn-info-header (str "user USER " id2))
            (request abs-uri2
                     :request-method :delete)
            (ltu/is-unset-cookie)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; create with invalid template fails
        (-> session-anon
            (request base-uri
                     :request-method :post
                     :body (json/write-str invalid-create))
            (ltu/body->edn)
            (ltu/is-status 400)))

      ;; admin create must also succeed
      (let [create-req (-> valid-create
                           (assoc-in [:sessionTemplate :username] "root")
                           (assoc-in [:sessionTemplate :password] "root"))
            resp (-> session-anon
                     (request base-uri
                              :request-method :post
                              :body (json/write-str create-req))
                     (ltu/is-set-cookie)
                     (ltu/body->edn)
                     (ltu/is-status 201))
            id (get-in resp [:response :body :resource-id])
            uri (-> resp
                    (ltu/location))
            abs-uri (str p/service-context (u/de-camelcase uri))]

        ;; admin should be able to see and delete session with session role
        (-> (session (ring-app))
            (header authn-info-header (str "root ADMIN " id))
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-absent "edit"))

        ;; admin can delete resource with session role
        (-> (session (ring-app))
            (header authn-info-header (str "root ADMIN " id))
            (request abs-uri
                     :request-method :delete)
            (ltu/is-unset-cookie)
            (ltu/body->edn)
            (ltu/is-status 200)))

      ;; admin create with invalid template fails
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str invalid-create))
          (ltu/body->edn)
          (ltu/is-status 400)))))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id session/resource-name))]
    (doall
      (for [[uri method] [[base-uri :options]
                          [base-uri :delete]
                          [resource-uri :options]
                          [resource-uri :put]
                          [resource-uri :post]]]
        (-> (session (ring-app))
            (request uri
                     :request-method method
                     :body (json/write-str {:dummy "value"}))
            (ltu/is-status 405))))))
