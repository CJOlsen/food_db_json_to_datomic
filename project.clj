(defproject usda_dbfront "0.1.0-SNAPSHOT"
  :description "A fairly simple json to datomic migration"
  :url "https://github.com/cjolsen/food_db_json_to_datomic"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"} ;; or GNU GPLv3
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.3"]
                 [com.datomic/datomic-free "0.8.4270"]])
