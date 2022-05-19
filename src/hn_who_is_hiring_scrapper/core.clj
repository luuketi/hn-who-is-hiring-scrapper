(ns hn-who-is-hiring-scrapper.core
  (:require [hickory.core :as h]
            [hickory.zip :as z]
            [hickory.render :as r]
            [clojure.zip :as zip]
            [hickory.select :as s]
            [clj-http.client :as client]
            [clojure.string :as str]))


(defn read-item [item]
  (->> item
       (s/select (s/class "comment"))
       first
       r/hickory-to-html))


(defn items [content]
  {:pre [(= 200 (:status content))
         (not (str/blank? (:body content)))]}
  (->>
    (str/replace (:body content) #"</div></div>" "</div>")
    h/parse
    h/as-hickory
    (s/select
      (s/descendant
        (s/class "comment-tree")
        (s/tag "tbody")))
    first
    :content
    (filter map?)
    (filter #(= "athing comtr" (:class (:attrs %))))
    (map read-item)
    ))


(def a (client/get "https://news.ycombinator.com/item?id=31235968&p=1"))
(items a)

