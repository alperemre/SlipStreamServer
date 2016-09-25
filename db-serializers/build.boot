(def +version+ "3.13-SNAPSHOT")

(set-env!
  :project 'com.sixsq.slipstream/SlipStreamDbSerializers-jar

  :version +version+
  :license {"Apache 2.0" "http://www.apache.org/licenses/LICENSE-2.0.txt"}
  :edition "community"

  :dependencies '[[org.clojure/clojure "1.8.0"]
                  [sixsq/build-utils "0.1.4" :scope "test"]])

(require '[sixsq.build-fns :refer [merge-defaults
                                   sixsq-nexus-url
                                   lein-generate]])

(set-env!
  :repositories
  #(reduce conj % [["sixsq" {:url (sixsq-nexus-url)}]])

  :dependencies
  #(vec (concat %
                (merge-defaults
                  ['sixsq/default-deps (get-env :version)]
                  '[[org.clojure/clojure]

                    [environ]

                    ; FIXME: need this one after requiring
                    ; com.sixsq.slipstream.ssclj.middleware.authn-info-header
                    [cheshire]

                    [org.clojure/data.xml]
                    [superstring]
                    [me.raynes/fs]

                    [com.sixsq.slipstream/SlipStreamPersistence]
                    [com.sixsq.slipstream/SlipStreamCljResources-jar]

                    ;; boot tasks
                    [boot-environ]
                    [adzerk/boot-test]
                    [adzerk/boot-reload]
                    [tolitius/boot-check]]))))

(require
  '[environ.boot :refer [environ]]
  '[adzerk.boot-test :refer [test]]
  '[adzerk.boot-reload :refer [reload]]
  '[tolitius.boot-check :refer [with-yagni
                                with-eastwood
                                with-kibit
                                with-bikeshed]])

(set-env!
  :source-paths #{"test"}
  :resource-paths #{"src" "test-resources"})

(task-options!
  pom {:project (get-env :project)
       :version (get-env :version)}
  test {:junit-output-to ""}
  install {:pom (str (get-env :project))}
  push {:pom (str (get-env :project))})

(def tests-assets-re #{#".*_test\.clj"
                       #"test_.*\.clj"
                       #"configuration.xml"})

(deftask run-tests
         "runs all tests and performs full compilation"
         []
         (comp
           (test)
           (sift :include tests-assets-re
                 :invert true)
           (aot :all true)))

(deftask build []
         (comp
           (pom)
           (sift :include tests-assets-re
                 :invert true)
           (aot :all true)
           #_(uber :exclude #{#"(?i)^META-INF/INDEX.LIST$"
                              #"(?i)^META-INF/[^/]*\.(MF|SF|RSA|DSA)$"
                              #".*log4j\.properties"})
           (jar)))

(deftask mvn-test
         "run all tests of project"
         []
         (run-tests))

(deftask mvn-build
         "build full project through maven"
         []
         (comp
           (build)
           (install)
           (target)))

(deftask mvn-deploy
         "build full project through maven"
         []
         (comp
           (mvn-build)
           (push :repo "sixsq")))