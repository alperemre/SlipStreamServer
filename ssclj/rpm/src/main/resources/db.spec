{:api-db                  {
                           :classname   "org.hsqldb.jdbc.JDBCDriver"
                           :subprotocol "hsqldb"
                           :subname     "hsql://localhost:9001/ssclj"
                           :make-pool?  true}
 :auth-db                 {
                           :classname   "org.hsqldb.jdbc.JDBCDriver"
                           :subprotocol "hsqldb"
                           :subname     "hsql://localhost:9001/slipstream"
                           :make-pool?  true}

 :token-nb-minutes-expiry 10080
 :passphrase              "sl1pstre8m"
 
 :upstream-server         "http://localhost:8182"}
