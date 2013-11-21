;; Author: Christopher Olsen
;; Copyright 2013
;; License: Eclipse or GNU GPLv3 (pick one)

(ns usda_dbfront.dblayer
  (:gen-class)
  (:use [usda_dbfront.json_to_datomic :as jtod]
        [datomic.api :only [q db] :as d]))


;; the connection is inherited from the json_to_datomic namespace
;; (def uri "datomic:mem://grubbery")   ;; choose location
;; (d/create-database uri)              ;; create db
;; (def conn (d/connect uri))           ;; get connection

;; Call json_to_datomic's function to migrate JSON to Datomic (should take less
;; than 2 minutes)
(defn migrate
  []
  "Call json_to_datomic's function to migrate JSON to Datomic (should take less
   than 2 minutes)"
  (make-datomic))

(migrate)

;; at this point the database has been migrated into datomic, conn is in this
;; namespace and you can query at will, here's a few samples but this is just
;; tip of the iceberg type stuff

(defn get-food-count
  []
  (count (q '[:find ?p :where [?p :food/id _]] (db conn))))

(defn get-food-groups
  []
  (flatten (vec (q '[:find ?g :where [_ :food/group ?g]] (db conn)))))

(defn get-food-manufacturers
  []
  (flatten (vec (q '[:find ?m :where [_ :food/manufacturer ?m]] (db conn)))))

(defn get-food-nutrient-names
  []
  (flatten (vec (q '[:find ?n :where [_ :food/nutrient ?nutr]
                                     [?nutr :food_nutrient/description ?n]]
                   (db conn)))))

(defn get-food-with-max-b12
  []
  (let [attr-id (q '[:find (max ?b)
                     :where [?f :food/nutrient ?nutr]
                            [?f :food/name ?n]
                            [?nutr :food_nutrient/description "Vitamin B-12"]
                            [?nutr :food_nutrient/value ?b]] (db conn))
        attr-id (first (first attr-id))]
    ;; can't be the most efficient, but ":find (max ?b) ?n" means means 
    ;; something different
    (q `[:find ?n :where [?f :food/name ?n]
                         [?f :food/nutrient ?nutr]
                         [?nutr :food_nutrient/description "Vitamin B-12"]
                         [?nutr :food_nutrient/value ~attr-id]] (db conn))))
(time (get-food-with-max-b12))
;; "Elapsed time: 18147.93153 msecs"
;; #{["Mollusks, clam, mixed species, cooked, moist heat"]}


;; that backtick in the second query above is questionable style, here's the same
;; thing done as a join:
(defn get-food-with-max-b12-2
  []
  (let [attr-id (q '[:find (max ?b)
                     :where [?f :food/nutrient ?nutr]
                            [?f :food/name ?n]
                            [?nutr :food_nutrient/description "Vitamin B-12"]
                            [?nutr :food_nutrient/value ?b]] (db conn))
        attr-id (first (first attr-id))]
    ;; can't be the most efficient, but ":find (max ?b) ?n" means means 
    ;; something different
    (q '[:find ?n :in $ ?attr-id 
                  :where [?f :food/name ?n]
                         [?f :food/nutrient ?nutr]
                         [?nutr :food_nutrient/description "Vitamin B-12"]
                         [?nutr :food_nutrient/value ?attr-id]]
       (db conn)
       attr-id))) ;; this is where the attr-id value enters the query 

(time (get-food-with-max-b12-2))
;; "Elapsed time: 35767.040778 msecs"  
;; #{["Mollusks, clam, mixed species, cooked, moist heat"]}
;; ** the time difference is most likely due to computer load

;; the last few functions should be possible with one query instead of two, but
;; I'm pretty new to Datalog

;; to wrap up, there's a great blog post with good query ideas at:
;; http://blog.datomic.com/2013/05/a-whirlwind-tour-of-datomic-query_16.html
