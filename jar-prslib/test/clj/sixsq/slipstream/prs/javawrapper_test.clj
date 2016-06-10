(ns sixsq.slipstream.prs.javawrapper-test
  (:require
    [clojure.test :refer :all]
    [sixsq.slipstream.prs.javawrapper :refer :all])
  (:import [java.util HashMap ArrayList HashSet]
           [com.sixsq.slipstream.persistence ImageModule DeploymentModule Node]))

(def cm {"a" "1" "b" "2" "c" "3"})
(def m (HashMap. cm))

(def cs #{"a" "A" "b" "B"})
(def s (HashSet. cs))

(def cl ["one" "two" "three"])
(def l (ArrayList. cl))

(def nested-map (HashMap. {"map" m "set" s "list" l}))

(deftest test-java-to-clj
  (is (= cm (java->clj m)))
  (is (= cs (java->clj s)))
  (is (= cl (java->clj l)))

  (is (map? (java->clj nested-map)))
  (is (= cm (get (java->clj nested-map) "map")))
  (is (= cs (get (java->clj nested-map) "set")))
  (is (= cl (get (java->clj nested-map) "list")))
  )

(deftest test-module-to-map
  (is (= "module/component" (:uri (module-to-map (ImageModule. "component")))))
  (is (= "module/application" (:uri (module-to-map (DeploymentModule. "application")))))
  )

(deftest test-process-module-compenent
  (let [m (process-module {:module (ImageModule. "component")
                           :foo    nil})]
    (is (contains? m :foo))
    (is (contains? m :module))
    (is (contains? (:module m) :uri))
    (is (contains? (:module m) :components))
    (is (= "module/component" (-> m :module :uri)))
    ))

(def app (doto (DeploymentModule. "application")
           (.setNode (Node. "node1" (ImageModule. "image1")))
           (.setNode (Node. "node2" (ImageModule. "image2")))
           (.setNode (Node. "node3" (ImageModule. "image3")))
           ))

(deftest test-comps-from-app
  (is (= 3 (count (comps-from-app app))))
  (is (contains? (first (comps-from-app app)) :uri))
  (is (= "module/image1" (->> (comps-from-app app)
                              (sort-by :uri)
                              (first)
                              :uri)))
  )