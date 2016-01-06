(ns com.sixsq.slipstream.auth.utils.http)

(defn response
  [code]
  { :status code
   :headers {"Content-Type" "text/plain"}})

(defn response-with-body
  [code body]
  { :status code
   :headers {"Content-Type" "text/plain"}
   :body body})

(defn response-redirect
  [url]
  (-> 307
      response
      (assoc-in [:headers "location"] url)))

(defn param-value
  [request key]
  (-> request
      :params
      (get key)))

(defn select-in-params
  [request keys]
  (-> request
      :params
      (select-keys keys)))

