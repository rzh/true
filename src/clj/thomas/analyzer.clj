(ns thomas.analyzer
  (:require [clojure.pprint :as p]
            [clj-time.coerce :as c]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [monger.core :as mg]
            [monger.collection :as mc]
            ))

(use '[clojure.string :only (join split)])
(use '[clojure.core.match :only (match)])
(use 'clojure.walk)

(import '(java.io BufferedReader StringReader))


;;-- helper functions
(def not-nil? (complement nil?))
(defn str-replace [pattern replacement string]
    (string/replace string pattern replacement))

(defn get-lines-from-string [s]
  (line-seq (BufferedReader. (StringReader. s))))

(defn get-db-name [ns]
  (if (not-nil? ns)
    (let [t (split ns #"\.")]
      (get t 0)
      )
    nil))

(defn read-json-pos [ll start-pos]
   (loop [pos start-pos depth 1]
       (if (zero? depth)
            pos
         (case (get ll (+ pos 1))
           "{" (recur (inc pos) (inc depth))
           ("}" "},") (recur (inc pos) (dec depth))
           nil pos
           (recur (inc pos) depth)
           )))   ; return the end-pos here for the json string here
  )


(defn read-json-string [l start-pos]
  (case (get l start-pos)
    "COLLSCAN" "{\"COLLSCAN\":\"COLLSCAN\"}"
    "EOF"  "{\"EOF\":\"EOF\"}"

    (str-replace #"new\" \"Date" "new-Date"
      (let [end-pos (read-json-pos l start-pos)]
        (join " " (map #(case %
                      ("{" "}" "}," ",") %
                      (->> (str "\"" % "\"")
                       (str-replace #"\"\""   "\"")
                       (str-replace #"_id"    "ID")
                       (str-replace #"\$"     "OP_")
                       (str-replace #"\'"     "\\'")
                       (str-replace #"\("     "\\(")
                       (str-replace #"\"\[\"" "[")
                       (str-replace #"\"\]\"" "]")
                       (str-replace #"\)"     "\\)")
                       (str-replace #"\",\""  "\",")
                       (str-replace #",\""    "\",")
                       (str-replace #":\""    "\":" )))
                (->> l (take (+ 1 end-pos)) (drop start-pos))))))))


;;-- filters
(defn f-all [x] true)
(defn f-update-only [x] (= (:op x) "update" ))
(defn f-query-only [x] (= (:op x) "query" ))
(defn f-insert-only [x] (= (:op x) "insert" ))
(defn f-cmd-only [x] (= (:op x) "command" ))
(defn f-getmore-only [x] (= (:op x) "getmore" ))

(defn f-namespace [n l]
  (filter #(= n (:ns %)) l))

;;-- utils for log
(defn process-key
  [k initial]
  (let [kp (re-matches #"^OP_(.)+$" (name k))]
    (if (not-nil? kp)
      ; this is operator, reformat it
      (str ":$" (str-replace #"OP_" "" (get kp 0)))

      ; not an operator, assuem fieldname
      (str (case (name k)
             "ID"       ":_id"
             "COLLSCAN" "$COLLSCAN"
             "EOF"      "$EOF"
             (if initial
               ; has to keep query & other potential keyward
               (case (name k)
                 "query"    "$query"
                 "orderby"  "$orderby"
                 ":fieldname"
                 )

               ; else just keyword the name
               ":fieldname")
             )))))


(defn normalize-map [m initial]
  (join ""
        (if (map? m)
            (for [[k v] (sort m)]
              (str (process-key k initial)
                   (if (map? v) (str "<" (normalize-map v false) ">")
                                (if (and (vector? v) (not= "OP_in" (name k)))
                                  ;(join ""
                                  ;    (for [i (range (/ (count v) 2))]
                                  ;      (normalize-map {(get v (* i 2)) (get v (+ 1 (* i 2)))} false)
                                  ;      ))
                                  (str "[" (join ""
                                        (for [i (range (count v))]
                                          (normalize-map (get v i) false))) "]")
                                  ""))))
            "#")))

(defn find-query [l]
  (let [sl (read-json-string l 5)]
    (json/read-json (read-json-string l 5))))

(defn find-query-string [l]
  (str-replace "\"" "" (read-json-string l 5)))



(defn find-update [l]
  (let [sl (read-json-string l (+ 2 (read-json-pos l 5)))]
    (json/read-json sl)))

(defn find-query-plan [l]
  (let [i (.indexOf l "planSummary:")]
    (if (< i 5)
      nil
      (get l (+ 1 i))
      )))

(defn epoch-second [t]
  (long (/ (c/to-long  t) 1000)))


(defn line-to-map [l]
  (try
  (let [t (split l #"\s")]
    (case (get t 2)
      "command"
      {:ts    (get t 0)
       :epoch (epoch-second (get t 0))
       :conn  (get t 1)
       :op    (get t 2)
       :ns    (get t 3)
       :s     l
       :cmd   (get t 5)}

      "update"
      {:ts     (get t 0)
       :epoch  (epoch-second (get t 0))
       :conn   (get t 1)
       :op     (get t 2)
       :s     l
       :ns     (get t 3)
       :q      (find-query t)
       :q_string (find-query-string t)
       :q_norm (normalize-map (find-query t) true)
       ;; :q_plan (find-query-plan t)
       :update (find-update t)
       :update_norm (normalize-map (find-update t) true)}

      "query"
      {:ts     (get t 0)
       :epoch  (epoch-second (get t 0))
       :conn   (get t 1)
       :s     l
       :op     (get t 2)
       :ns     (get t 3)
       :q_norm (str (normalize-map (find-query t) true)
                    (if (not= nil (find-query-plan t)) (str "&" (find-query-plan t))))
       :q_string (find-query-string t)
       :q_plan (find-query-plan t)
       :q      (find-query t)}

      ("insert" "remove" "getmore")
      {:ts    (get t 0)
       :epoch (epoch-second (get t 0))
       :conn  (get t 1)
       :op    (get t 2)
       :s     l
       :ns    (get t 3)}

      ;; default is nothing
      nil))
    (catch Exception e (str "caught exception: " (.getMessage e) "++ for input " l))))


;;-- load related helpers

(defn get-unique-ts-in-second [d f]
  (frequencies (map #(:epoch %) (filter f d))))

(defn get-unique-op [d]
  (frequencies (map #(:op %) d)))

(defn get-unique-ns [d]
  (frequencies (map #(:ns %) (filter not-nil? d))))

(defn get-unique-db [d]
  (frequencies (map #(get-db-name (:ns %)) (filter not-nil? d))))

(defn get-unique-cmd [d]
  (frequencies (map #(:cmd %) (filter not-nil? d))))


(defn op-read
  [l]
  (case (:op l)
    "command" (:cmd l)
    "update" (str "update" (match [(:update l)]
                                  [{:OP_inc _}]  "-$inc"
                                  [{:OP_set _}]  "-$set"
                                  [{:OP_pull _}] "-$pull"
                                  :else "update"
                             ))
    (:op l))) ;; end of op-read


;;--  ns related utils

(defn analyze-ns-load [nsp d]
  (frequencies (map #(op-read %) (filter #(= (:ns %) nsp) d)))
  )

(defn get-unique-ns-load [d]
  (sort (into #{} (map (fn [e] [(key e) (analyze-ns-load (key e) d)] ) (get-unique-ns d)))))



;;-- shape related utils
(defn analyze-bson-shape [td _key]
  (into {} (for [[k v] (group-by _key td)]
                 [k {:ns (count (distinct (map #(:ns %) v)))
                     :count (count v)
                     :q (take 1 (distinct (map #(:q %) v)))
                     :query-plan (distinct (map #(:q_plan %) v))
                     :sample (take 1 (distinct (map #(:s %) v)))}]
               ))) ;;":fieldname+:fieldname:fieldname-:fieldname+:$OP_and-"))

(defn analyze-query-shape [td]
  (analyze-bson-shape (filter f-query-only td) :q_norm)
  )

(defn analyze-update-shape [td]
  (analyze-bson-shape (filter f-update-only td ):u_norm)
  )




