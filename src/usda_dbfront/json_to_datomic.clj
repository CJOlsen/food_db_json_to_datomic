;;
;; Author: Christopher Olsen
;; Copyright 2013
;; License: Eclipse or GNU GPLv3 (choose one, both included in project)
;;

;; This is a file to convert the 2011 USDA foods database from JSON to Datomic.
;; It was compiled into JSON form by Ashley Williams 
;; (http://ashleyw.co.uk/project/food-nutrient-database) and is in the public
;; domain (to the extent of my knowledge.)


(ns usda_dbfront.json_to_datomic
  (:gen-class))

(require '[clojure.data.json :as json])
(use '[datomic.api :only [q db] :as d])

;; create datomic in memory database (change the uri for different db options)
(def uri "datomic:mem://grubbery")   ;; choose location
(d/create-database uri)               ;; create db
(def conn (d/connect uri))            ;; get connection



(def schema
  (read-string
   (slurp "resources/datomic-schema.dtm")))

(defn add-schema
  []
  @(d/transact conn schema))

(defn add-data
  "Add one datom to the datomic database."
  [datom]
  @(d/transact conn datom))


;; load the json file: 

(def ^:dynamic *json-loc* "resources/foods-2011-10-03.json")
(def all-json (json/read-str (slurp *json-loc*) :key-fn keyword))

;; (count all-json)
;; => 6636

;; functions for transferring from json --> datomic
;; ** see notes on (make-transaction) below before continuing **
(defn add-single
  "Add one item to the new datomic transaction"
  ;; t-map == datomic transaction-map, j-item == json item
  [t-map j-item old-name new-name] ;; old-name and new-name == keywords
  (let [temp (old-name j-item)]
    (if (= 0 (count temp))
      t-map
      (assoc t-map new-name temp))))

(defn add-multiple
  "Add multiple items to the new datomic transaction"
  [t-map j-item old-name new-name]
  (loop [temp (old-name j-item)
         t-map t-map]
    (cond (= 0 (count temp)) t-map
          :else (recur (rest temp) (assoc t-map new-name (first temp))))))

(defn add-id
  [t-map j-item]
  (assoc t-map :food/id (:id j-item)))

(defn add-description
  [t-map j-item]
  (add-single t-map j-item :description :food/name))

(defn add-manufacturer
  [t-map j-item]
  (add-single t-map j-item :manufacturer :food/manufacturer))

(defn add-group
  [t-map j-item]
  (add-single t-map j-item :group :food/group))

(defn add-tags
  [t-map j-item]
  (add-multiple t-map j-item :tags :food/tag))

(defn portion->dat
  "Translate data from a JSON portion for the datomic transaction."
  [json-portion id-number]
  [{:db/id #db/id[:db.part/user -1000001]  ;; food temp-id = -1000001
    :food/portion (d/tempid :db.part/user id-number)}
   {:db/id (d/tempid :db.part/user id-number)
    :portion/amount (:amount json-portion)
    :portion/unit (:unit json-portion)
    :portion/grams (:grams json-portion)}])

(defn add-portions
  [t json-item]
  (loop [portion-id -1000101 ;; portion temp-id's start at -1000101
         portions (:portions json-item)
         transaction t]
    (cond (= 0 (count portions)) transaction
          :else (recur (- portion-id 1)
                       (rest portions)
                       (into transaction (portion->dat (first portions)
                                                       portion-id))))))

(defn nutrient->dat
  "Translate data from a JSON nutrient for the datomic transaction."
  [json-nutrient id-number]
  [{:db/id #db/id[:db.part/user -1000001]
    :food/nutrient (d/tempid :db.part/user id-number)}
   {:db/id (d/tempid :db.part/user id-number)
    :food_nutrient/description (:description json-nutrient)
    :food_nutrient/units (:units json-nutrient)
    :food_nutrient/value (:value json-nutrient)
    :food_nutrient/group (:group json-nutrient)}])

(defn add-nutrients
  [t json-item]
  (loop [nutrient-id -1000501 ;; nutrient temp-id's start at -1000501
         nutrients (:nutrients json-item)
         transaction t]
    (cond (= 0 (count nutrients)) transaction
          :else (recur (- nutrient-id 1)
                       (rest nutrients)
                       (into transaction (nutrient->dat (first nutrients)
                                                        nutrient-id))))))
(defn print-inline
  "Thread macro debugging tool"
  [current]
  (println "print-inline: " current)
  current)


;; A few notes on (make-transaction):
;; Each food in the database gets one loop which is defined by make-transaction.
;; In that loop a Datomic transaction is built from scratch, it's a vector of
;; hash-maps with corresponding temp-id's that let Datomic know how the 
;; different entities are related to eachother.  
;; In this loop, the singular attributes are picked up first from json, each is
;; added to the first hash-map.  Then the nutrients and portions are picked up,
;; portion numbers vary (and I suspect nutrients do too but either way I've 
;; assumed that they do).  The nutrients and the portions get their own subloops
;; that go through however many of each are defined in the json db.  
;; The temp-id's start over at the end of each loop of make-transaction because
;; they are converted to permanent id's when the transaction is run (which 
;; means we're running 6636 unique transactions instead of building one big 
;; one.)

(defn make-transaction
  "Takes a json item and converts it to a datomic transaction."
  [json-item]
  ;; :db/id is reused because it's mapped to a permanent id for each transaction
  ;; food is always: #db/id[:db.part/user -1000001]
  (-> {:db/id #db/id[:db.part/user -1000001]}
      (#(add-id % json-item))
      (#(add-description % json-item))
      (#(add-tags % json-item))
      (#(add-manufacturer % json-item))
      (#(add-group % json-item))
      ((fn [x] [x])) ;; make the current map the first map in a vector of maps
      (#(add-portions % json-item))
      (#(add-nutrients % json-item))))


(defn json->datomic-memsave
  "This function does the work of transferring data from json -> datomic.  It 
   loops once for each food.  Any foods that throw errors are collected and
   returned at the end.  Loop/recur is necessary to keep within the memory 
   bounds of the JVM"
  [json-items]
  (loop [current (first json-items)
         waiting (rest json-items)
         errors  []]
    ;; if there's no error nil gets conj'd onto errors (filtered out later)
    (let [result (try (doall (add-data (make-transaction current)) nil)
                      (catch Exception e (println e) current))
          errors (conj errors result)]
      ;(Thread/sleep 200) ;; optional depending on your hardware
      (cond (empty? waiting) (filter #(not (nil? %)) errors)
            :else (recur (first waiting)
                         (rest waiting)
                         errors)))))

(defn make-datomic
  []
  (add-schema)
  (time (def the-errors (doall (json->datomic-memsave all-json)))))
;; user=> "Elapsed time: 70869.487112 msecs"
;;
;; A little over a minute, impressive!

;; #'user/the-errors
;; (count the-errors)
;; 0

;; count the food groups
;; (count (q '[:find ?p :where [?p :food/group _]] (db conn)))
;; 6636, same as the JSON db


;; count the food portions
;; (count (q '[:find ?p :where [_ :food/portion ?p]] (db conn)))
;; 11145, reasonable - average of ~two portion choices per food

;; count the food nutrients
;; (count (q '[:find ?n :where [_ :food/nutrient ?n]] (db conn)))
;; 389355, reasonable - about 60 nutrients per food




;;
;; Notes on a Datomic Pro Starter version (not included in this project)
;;

;; Below are Datomic Pro Starter times (you have to register with Datomic to 
;; get pro, these times are based on transferring to a Postgres SQL key-value 
;; store)
;; I also had to add a 200ms delay for each food to give my hardware a chance
;; to catch up (it's an old computer and running a JVM, a transactor and 
;; Postgres at the same time was a bit much.)  The 200ms added a little over
;; 22 minutes to the ~29 minute transfer.  So 7-8 minutes to migrate the 
;; database to Datomic backed by Postgres, a little over a minute for the 
;; in memory database.
;;
;; "Elapsed time: 1755764.982133 msecs"
;; #'user/the-errors
;; (count the-errors)
;; 0

;; count the food groups
;; (count (q '[:find ?p :where [?p :food/group _]] (db conn)))
;; 6636, same as the JSON db


;; count the food portions
;; (count (q '[:find ?p 
;;             :where [_ :food/portion ?p]] (db conn)))
;; 11145, reasonable - average of ~two portion choices per food

;; count the food nutrients
;; (count (q '[:find ?n
;;             :where [_ :food/nutrient ?n]] (db conn)))
;; 389355, reasonable - about 60 nutrients per food


;; In postgres:
;; datomic=> SELECT pg_database_size('datomic');
;;  pg_database_size 
;; ------------------
;;          45352740
;; (1 row)
;;
;; datomic=> SELECT pg_size_pretty(pg_database_size('datomic'));
;;  pg_size_pretty 
;; ----------------
;;  43 MB
;; (1 row)
