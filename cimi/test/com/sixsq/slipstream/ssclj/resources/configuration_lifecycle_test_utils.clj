(ns com.sixsq.slipstream.ssclj.resources.configuration-lifecycle-test-utils
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.configuration :refer :all]
    [com.sixsq.slipstream.ssclj.resources.configuration-template :as ct]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer :all]))

(def base-uri (str p/service-context (u/de-camelcase resource-name)))


(defn check-lifecycle
  [service attr-kw attr-value attr-new-value]

  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-anon (header session authn-info-header "unknown ANON")
        session-user (header session authn-info-header "jane USER ANON")
        session-admin (header session authn-info-header "root ADMIN USER ANON")

        name-attr "name"
        description-attr "description"
        properties-attr {:a "one", :b "two"}

        href (str ct/resource-url "/" service)
        template-url (str p/service-context ct/resource-url "/" service)
        resp (-> session-admin
                 (request template-url)
                 (ltu/body->edn)
                 (ltu/is-status 200))
        template (get-in resp [:response :body])
        valid-create {:name                  name-attr
                      :description           description-attr
                      :properties            properties-attr
                      :configurationTemplate (ltu/strip-unwanted-attrs (assoc template attr-kw attr-value))}
        href-create {:configurationTemplate {:href   href
                                             attr-kw attr-value}}
        invalid-create (assoc-in valid-create [:configurationTemplate :invalid] "BAD")]

    ;; anonymous create should fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-create))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; user create should also fail
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-create))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; admin create with invalid template fails
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str invalid-create))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; full configuration lifecycle as administrator should work
    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-create))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/is-location)
                  (ltu/location))
          abs-uri (str p/service-context (u/de-camelcase uri))]

      ;; admin get succeeds
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; check the contents
      (let [{:keys [name description properties] :as body} (-> session-admin
                                                               (request abs-uri)
                                                               (ltu/body->edn)
                                                               :response
                                                               :body)]
        (is (= name name-attr))
        (is (= description description-attr))
        (is (= properties properties-attr)))

      ;; anonymous query fails
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; admin query succeeds
      (let [service-matches? (fn [{entry-service :service}] (= service entry-service))
            entries (-> session-admin
                        (request base-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/is-resource-uri collection-uri)
                        (ltu/entries resource-tag))]
        (is ((set (map :id entries)) uri))
        (is (= 1 (count (filter service-matches? entries))))

        ;; verify that all entries are accessible
        (let [pair-fn (juxt :id #(str p/service-context (:id %)))
              pairs (map pair-fn entries)]
          (doseq [[id entry-uri] pairs]
            (-> session-admin
                (request entry-uri)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-id id)))))

      ;; try editing the configuration
      (let [old-cfg (-> session-admin
                        (request abs-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        :response
                        :body)
            old-flag (get old-cfg attr-kw)
            new-cfg (assoc old-cfg attr-kw attr-new-value)
            _ (-> session-admin
                  (request abs-uri
                           :request-method :put
                           :body (json/write-str new-cfg))
                  (ltu/is-status 200))
            reread-attr-value (-> session-admin
                                  (request abs-uri)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  :response
                                  :body
                                  (get attr-kw))]
        (is (not= old-flag reread-attr-value))
        (is (= attr-new-value reread-attr-value)))

      ;; admin delete succeeds
      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; ensure entry is really gone
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))

    ;; abbreviated lifecycle using href to template instead of copy
    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str href-create))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))
          abs-uri (str p/service-context (u/de-camelcase uri))]

      ;; admin delete succeeds
      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; ensure entry is really gone
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))))


(defn check-bad-methods
  []
  (let [resource-uri (str p/service-context (u/new-resource-id resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
