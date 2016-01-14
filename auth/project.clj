(defproject com.sixsq.slipstream/auth "2.22-SNAPSHOT"
  :description  "Authentication Service"
  :url          "http://sixsq.com"

  :license {:name "Apache License, Version 2.0"
            :url  "http://www.apache.org/licenses/LICENSE-2.0"}

  :resource-paths ["resources"
                   "test/resources"]

  :dependencies [[org.clojure/clojure                       "1.7.0"]
                 [org.clojure/data.json                     "0.2.6"]
                 [superstring                               "2.1.0"]

                 ;; buddy
                 [buddy/buddy-core                          "0.6.0"]
                 [buddy/buddy-hashers                       "0.6.0"]
                 [buddy/buddy-sign                          "0.6.0"]

                 ;; HTTP
                 [clj-http                                  "2.0.0"]

                 ;; logging
                 [org.clojure/tools.logging                 "0.3.0"]
                 [log4j/log4j                               "1.2.17"
                                        :exclusions [ javax.mail/mail
                                                      javax.jms/jms
                                                      com.sun.jdmk/jmxtools
                                                      com.sun.jmx/jmxri]]
                 ;; Environment settings
                 [environ                                   "1.0.0"]

                 ;; database
                 [org.clojure/java.jdbc                     "0.3.7"]
                 [korma                                     "0.4.2"]
                 [org.hsqldb/hsqldb                         "2.3.2"]
                 [org.xerial/sqlite-jdbc                    "3.7.2"]

                 ]

  :plugins [[lein-environ "1.0.0"]]

  :profiles {
             :uberjar  {  :aot [#"com.sixsq.slipstream.auth.*"]
                          :env {  :clj-env        :production
                                  :config-path "config-hsqldb-mem.edn" }
                          :jvm-opts ["-Dlogfile.path=production"]}


             :provided {:dependencies [[reply/reply "0.3.4"]]}

             :dev      {  :env          { :clj-env        :development
                                          :config-path "config-hsqldb-mem.edn"
                                          :passphrase  "sl1pstre8m"}
                          :jvm-opts     ["-Dlogfile.path=development"]
                          :dependencies [[peridot/peridot "0.3.0"]]}

             :test     {  :env          {:clj-env     :test
                                         :config-path "config-hsqldb-mem.edn"
                                         :passphrase  "sl1pstre8m"}
                          :jvm-opts     ["-Dlogfile.path=test"]
                          :dependencies [[peridot/peridot "0.3.0"]]}})
