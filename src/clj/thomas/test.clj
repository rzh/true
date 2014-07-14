(ns thomas.test
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.pprint :as p]
            [clojure.string :as string]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [miner.herbert :as h]
            [miner.herbert.generators :as hg]
            [clojure.pprint :as p]
            [thomas.analyzer :as a]
            [clojure.test.check.properties :as prop])
  )


(def db (mg/get-db (mg/connect) "test"))


(defn mongo-query-map [_val db coll]
  (mc/remove db coll) ;; remove all doc
  (if (= (:cid _val) 100)
    (mc/insert db coll {:cid 101} )
    (mc/insert db coll _val )
    )

  (= _val (apply dissoc (mc/find-one-as-map db coll _val) [:_id])))

;; test query shapes
;; :a int
(defn mongo-query-key [_val _key db coll]
  (mc/remove db coll) ;; remove all doc

  (mc/ensure-index db coll (array-map _key 1) { :name "by-key" })

  (mc/insert db coll {_key _val} )
  (= _val (_key (mc/find-one-as-map db coll {_key _val}))))



(defn mongo-test-vector
  [_val db coll]

  ;;- remove all doc in the collection
  (mc/remove db coll)

  ;;- insert the doc
  (mc/insert db coll {:a _val} )

  ;;- return query results without _id
  (:a (mc/find-one-as-map db coll {:a _val}))
  )


;;-- prop for quick check
(def mongo-insert-query-prop-key
  (prop/for-all [v gen/int]
                (= v (mongo-query-key v :a db "test1"))))

(def mongo-insert-query-prop-map
  (prop/for-all [v (gen/map gen/keyword  gen/int)]
                (= v (mongo-query-map v :a db "test1"))))


(def mongo-insert-query-prop-vector
  (prop/for-all [v (gen/not-empty (gen/vector gen/int))]
                	(= v (mongo-test-vector v db "test1"))
                ))


;;-- check
(prn (tc/quick-check 100  mongo-insert-query-prop-key ))


;;-- helpers
(defn gen-map-from-shape
  "to create a map from shape"
  [shape n & {:keys [query]
              :or {query false}}]
  ; to file the all map vals with n
    (into {} (for [[k v] shape]
               (cond
                (map? v)
                 [k (gen-map-from-shape v n)]
                (vector? v) [k (cond
                             	  (some #(= :rand-int %) v)
                                	(if (and query (some #(or (= :lte %) (= :gt %)) v))
                                      {$lte (rand-int 1000)}
                                      (rand-int 1000))

                			      (some #(= :rand %) v)
				                     (rand 1000)
                                  :else 10001)
                             ]
                (= :ObjectId v)
                [k (str "ObjectId(" n ")")]

                (= :string v)
                [k (str "string_" n)]
                :else
                 [k n]))))


(defn find-query-field
  "to get query from analyer
    if has :OP_query use it
    otherwise assume all is query"
  [q]
  (if (contains? q :OP_query)
    (:OP_query q)
    q))

(defn query->schema
  "generate data schema from query"
  [q]
  (let [query (find-query-field (:q q))]
    (into {} (for [ [k v] query]
               (cond
                (instance? String v)
                  (cond
                   (re-matches #"ObjectId.*" v)
                   {k :ObjectId}
                   :else
                   {k :string})
                (contains? v :OP_lte) {k [:rand-int :lte]}
                :else {k v}
                )))))



(defn query->perfschema
"generate data schema from query"
[q]
(let [query (find-query-field q)]
  (into {} (for [ [k v] query]
             (cond
              (instance? String v)
                (cond
                 (re-matches #"ObjectId.*" v)
                 {k 'int}
                 :else
                 {k 'str})
              (contains? v :OP_lte) {k 'int}
              (contains? v :OP_gte)  {k 'int}
              :else {k 'int}  ;; default to int
              )))))


(defn schema->benchrun
  "to generate benchrun script from schema
  a sample mongo-perf script
         tests.push( { name : \"Queries.Empty\",
              pre: function( collection ) {
                  collection.drop();
                  for ( var i = 0; i < 1000; i++ ) {
                      collection.insert( {} );
                  }
              },
              ops : [
                  { op: \"find\", query: {} }
              ] } );
  "
  [sch]
  (str
  "tests.push( { name : \"Queries.quick-check\",
              pre: function( collection ) {
                  collection.drop();
                  for ( var i = 0; i < 1000; i++ ) {
                      collection.insert( {"
   ;; insert str here

   (a/str-replace #"str" "toString(i)"
       (a/str-replace #"int" "i" (string/join " " (map name (apply concat sch)))))

   "} );
                  }
              },
              ops : [
                  { op: \"find\", query: {"
   ;; query string here
     (a/str-replace #"str" "toString(#RAND_INT)"
       (a/str-replace #"int" "#RAND_INT: [ 0, 1000 ]" (string/join " " (map name (apply concat sch)))))
   "} }
              ] } );
  "

  ))


;; (p/pprint (schema->benchrun sch))


;; (a/str-replace #"int" "i"(string/join " " (map name (apply concat sch))))




;; (def y {:q {:OP_query {:groupId "ObjectId('538fa43177143b548c76ecfd')", :active "true"}, :OP_orderby {:name "1"}}, :epoch 1401923247, :ns "mmsdbconfig.config.hostClusters", :q_plan "IXSCAN", :op "query", :q_norm ":$orderby<:fieldname>:$query<:fieldname:fieldname>&IXSCAN", :s "2014-06-04T23:07:27.745+0000 [conn50] query mmsdbconfig.config.hostClusters query: { $query: { groupId: ObjectId('538fa43177143b548c76ecfd'), active: true }, $orderby: { name: 1 } } planSummary: IXSCAN { groupId: 1, name: 1 } ntoreturn:20 ntoskip:0 keyUpdates:0 numYields:0 locks(micros) r:118 nreturned:0 reslen:20 0ms", :q_string "{ OP_query: { groupId: ObjectId('538fa43177143b548c76ecfd'), active: true }, OP_orderby: { name: 1 } }", :ts "2014-06-04T23:07:27.745+0000", :conn "[conn50]"})


;; (quick-check-query [y])

;; (def sch (query->perfschema (:q y)))


;; (p/pprint (take 30 (gen/sample (hg/generator sch))))


;;---

(defn mongo-query-check-with-schema
  [sch]
  (prop/for-all [v (gen/not-empty (hg/generator sch))]
                	 (mongo-query-map v db "test1")
                ))


(defn quick-check-query
  "run quick check for query, and return a bool"
  [q]

;;   (prn "quick-check for query "
;;        (:q (get q 0))
;;        " with schema "
;;        (query->perfschema (:q (get q 0))))
    (tc/quick-check 500  (mongo-query-check-with-schema (query->perfschema (:q (get q 0)))) :seed 1405102093780 )
  )

;; (quick-check-query [y])
;; (def sch (query->perfschema (:q y)))



;; (def re (tc/quick-check 1000  (mongo-query-check-with-schema  sch)  ))

