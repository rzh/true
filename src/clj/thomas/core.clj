(ns thomas.core
  (:require [ring.util.response :refer [file-response]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.edn :refer [wrap-edn-params]]
;;            [ring.middleware.json :refer :all]
            [compojure.core :refer [defroutes GET PUT POST]]
            [compojure.route :as route]
            [thomas.analyzer :as a]
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
         :query-plan (distinct (map #(:q_plan %) v))
         }))))

(defn return-new-queries []
  (prn (str "log is " (analyze-log)))

  (generate-response {:queries (analyze-log) }))

(defn return-quick-check-results []
  (generate-response {:results [
                                {:shape "_id" :result "{_id: 100}"}
                                {:shape "fieldname" :result "{a: 100}"}
                                ]}))

(defn return-perf-check-results []
  (generate-response {:results [
                                {:shape "_id" :result "{_id: 100}"}
                                {:shape "fieldname" :result "{a: 100}"}
                                ]}))

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
