(ns thomas.core
  (:require [ring.util.response :refer [file-response]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.edn :refer [wrap-edn-params]]
;;            [ring.middleware.json :refer :all]
            [compojure.core :refer [defroutes GET PUT POST]]
            [compojure.route :as route]
            [thomas.analyzer :as a]
            [thomas.test :as t]
            [compojure.handler :as handler]))


(def _log (atom ""))

(defn index []
  (file-response "public/html/index.html" {:root "resources"}))


(defn generate-response [data & [status]]
  (Thread/sleep 1000)
  {:status (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body (pr-str data)})

(defn do-something [a b])



(defn upload-log
  "receive the log"
  [log]
  (prn (str "received " log))
  (reset! _log log)
  (generate-response {:uploaded log} 200)
  )


(defn analyze-log []
  (let [x (map a/line-to-map (a/get-lines-from-string @_log))]
    (into []
      (for [[k v] (group-by :q_norm x)]
        {:shape  k
         :sample (take 1 (distinct (map #(:s %) v)))
         :ns (count (distinct (map #(:ns %) v)))
         :count (count v)
         :q (take 1 (distinct (map #(:q %) v)))
         :schema (t/query->schema (:q (get v 0)))
         :query-plan (distinct (map #(:q_plan %) v))
         }))))

(defn return-new-queries []
;;  (prn (str "log is " (analyze-log)))

  (generate-response {:queries (analyze-log) }))


(defn run-quick-check
  []
  (let [x (map a/line-to-map (a/get-lines-from-string @_log))]
    (into {}
      (for [[k v] (group-by :q_norm x)]
        (let [re
              (t/quick-check-query v)]
          (prn "___ " re)
          [ k {:result (if (:result re) "pass" "fail")
               :qc-log (str re)
               :schema (t/query->perfschema (:q (get v 0)))}]
          )
        )
  )))

(defn run-perf-check
  []
  (let [x (map a/line-to-map (a/get-lines-from-string @_log))]
    (into {}
      (for [[k v] (group-by :q_norm x)]
        [ k { :result "pass"
              :script (t/schema->benchrun (t/query->perfschema (:q (get v 0))))
              }]
        )
  )))


(defn return-quick-check-results []
  (generate-response {:results (run-quick-check)}))

(defn return-perf-check-results []
  (generate-response {:results (run-perf-check)}))

(defroutes routes
  (GET "/" [] (index))

  (GET "/new-queries" [] (return-new-queries))
  (GET "/quick-check" [] (return-quick-check-results))
  (GET "/perf-check" [] (return-perf-check-results))

  ;;-- upload log
  (PUT "/log" [log] (upload-log  log))

  (PUT "/class/:id/update"
    {params :params edn-params :edn-params}
    (do-something (:id params) edn-params))

  (route/files "/" {:root "resources/public"}))

(def app
  (-> routes
      wrap-edn-params))

(defonce server
  (run-jetty #'app {:port 8080 :join? false}))


(defn restart-server []
  (.stop server)
  (.start server))


(restart-server)
