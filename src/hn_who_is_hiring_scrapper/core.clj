(ns hn-who-is-hiring-scrapper.core
  (:require [hickory.core :as h]
            [hickory.zip :as z]
            [clojure.zip :as zip]
            [hickory.select :as s]
            [clj-http.client :as client]
            [clojure.string :as str]))


(defn items [content]
  {:pre [
         (= 200 (:status content))
         (not (str/blank? (:body content)))
         ]
   }
  (->> content
       :body
       h/parse
       h/as-hickory
       (s/select
         (s/descendant
           (s/class "comment-tree")
           (s/tag "tbody")))
       first
       :content
       (filter map?)
       ))

