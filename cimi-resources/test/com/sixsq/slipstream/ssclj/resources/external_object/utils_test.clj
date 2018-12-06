(ns com.sixsq.slipstream.ssclj.resources.external-object.utils-test
  (:require
    [clojure.string :as s]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.resources.external-object.utils :as u])
  (:import (com.amazonaws AmazonServiceException)))


(deftest test-generate-url
  (let [os-host "s3.cloud.com"
        obj-store-conf {:endpoint (str "https://" os-host)
                        :key      "key"
                        :secret   "secret"}
        bucket "bucket-name"
        obj-name "object/name"
        verb :put]
    (is (s/starts-with? (u/generate-url obj-store-conf bucket obj-name verb)
                        (format "https://%s.%s/%s?" bucket os-host obj-name)))))


(deftest add-size-or-md5sum

  (with-redefs [u/s3-object-metadata (fn [_ _ _] {:contentLength 1, :contentMD5 "aaa"})]
    (is (= {:size 1} (u/add-s3-size {} nil nil nil)))
    (is (= {:size 1} (u/add-s3-size nil nil nil nil)))
    (is (= {:size 1} (u/add-s3-size {:size 99} nil nil nil)))
    (is (= {:md5sum "aaa"} (u/add-s3-md5sum {} nil nil nil)))
    (is (= {:mykey "myvalue" :size 1 :m5sum "aaa"}) (-> {:myKey "myvalue"}
                                                        (u/add-s3-size nil nil nil)
                                                        (u/add-s3-md5sum nil nil nil))))

  (with-redefs [u/s3-object-metadata (fn [_ _ _] {:contentLength 2, :contentMD5 nil})]
    (is (= {:size 2} (u/add-s3-size {} nil nil nil)))
    (is (= {:size 2} (u/add-s3-size nil nil nil nil)))
    (is (= {:size 2} (u/add-s3-size {:size 99} nil nil nil)))
    (is (= {} (u/add-s3-md5sum {} nil nil nil)))
    (is (= {:mykey "myvalue" :size 2}) (-> {:myKey "myvalue"}
                                           (u/add-s3-size nil nil nil)
                                           (u/add-s3-md5sum nil nil nil))))

  (with-redefs [u/s3-object-metadata (fn [_ _ _] {:contentLength nil, :contentMD5 nil})]
    (is (= {} (u/add-s3-size {} nil nil nil)))
    (is (= nil (u/add-s3-size nil nil nil nil)))
    (is (= {:size 99} (u/add-s3-size {:size 99} nil nil nil)))
    (is (= {} (u/add-s3-md5sum {} nil nil nil)))
    (is (= {:mykey "myvalue"}) (-> {:myKey "myvalue"}
                                   (u/add-s3-size nil nil nil)
                                   (u/add-s3-md5sum nil nil nil))))

  (with-redefs [u/s3-object-metadata (fn [_ _ _] nil)]
    (is (= {} (u/add-s3-size {} nil nil nil)))
    (is (= nil (u/add-s3-size nil nil nil nil)))
    (is (= {:size 99} (u/add-s3-size {:size 99} nil nil nil)))
    (is (= {} (u/add-s3-md5sum {} nil nil nil)))
    (is (= {:mykey "myvalue"}) (-> {:myKey "myvalue"}
                                   (u/add-s3-size nil nil nil)
                                   (u/add-s3-md5sum nil nil nil))))


  (with-redefs [u/s3-object-metadata (fn [_ _ _] (let [ex (doto
                                                            (AmazonServiceException. "Simulated AWS Exception for S3 permission error")
                                                            (.setStatusCode 403))]
                                                   (throw ex)))]
    (is (= {} (u/add-s3-size {} nil nil nil)))
    (is (= nil (u/add-s3-size nil nil nil nil)))
    (is (= {:size 99} (u/add-s3-size {:size 99} nil nil nil)))
    (is (= {} (u/add-s3-md5sum {} nil nil nil)))
    (is (= {:mykey "myvalue"}) (-> {:myKey "myvalue"}
                                   (u/add-s3-size nil nil nil)
                                   (u/add-s3-md5sum nil nil nil)))))
