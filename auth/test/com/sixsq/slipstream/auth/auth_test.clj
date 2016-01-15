(ns com.sixsq.slipstream.auth.auth-test
  (:require
    [clojure.test :refer :all]
    [com.sixsq.slipstream.auth.auth :as auth]
    [com.sixsq.slipstream.auth.test-helper :as th]))

(def valid-credentials {:user-name "super" :password "supeRsupeR"})
(def valid-request {:params (merge {:authn-method :internal} valid-credentials)})

(defn fixture-delete-all
  [f]
  (th/create-test-empty-user-table)
  (f))

(use-fixtures :each fixture-delete-all)

(deftest test-auth-internal-invalid-credentials
  (th/add-user-for-test! valid-credentials)
  (is (= 401 (:status (auth/login {:params {:authn-method :internal}}))))
  (is (= 401 (:status (auth/login {:params {:authn-method :internal :user-name "super" :password "wrong"}})))))

(deftest test-auth-internal-valid-credentials
  (th/add-user-for-test! valid-credentials)
  (is (= 200 (:status (auth/login valid-request))))
  (is (get-in (auth/login valid-request) [:cookies "com.sixsq.slipstream.cookie" :value :token])))

(deftest test-auth-logout
  (let [logout-response (auth/logout valid-request)
        cookies (get-in logout-response [:cookies "com.sixsq.slipstream.cookie"])]
    (is (= 200 (:status logout-response)))
    (is (= "INVALID" (:value cookies)))
    (is (= 0 (:max-age cookies)))))

