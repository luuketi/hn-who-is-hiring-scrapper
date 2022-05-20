(ns hn-who-is-hiring-scrapper.core
  (:require [hickory.core :as h]
            [hickory.zip :as z]
            [hickory.render :as r]
            [clojure.zip :as zip]
            [hickory.select :as s]
            [clj-http.client :as client]
            [clojure.string :as str]))


(defn retrieve-all-pages [page-id]
  (let [url "https://news.ycombinator.com/%s"]
    (loop [href (format "item?id=%s" page-id)
           contents []]
      (println "=> " href)
      (let [url (format url href)
            raw-content (client/get url)
            content (->> raw-content :body h/parse h/as-hickory)
            new-href (->> content (s/select (s/class "morelink")) first :attrs :href)]
        (if (nil? new-href)
          (conj contents raw-content)
          (recur new-href (conj contents raw-content))
          )))))

(defn read-item [item]
  (when (zero? (->> item (s/select (s/class "ind")) first :attrs :indent Integer/parseInt))
    (->> item (s/select (s/class "comment")) first r/hickory-to-html)))

(defn concat-items [coll item]
  (conj coll "<tr><td>" item "<hr width=\"90%\" color=\"green\"></tr></td>"))

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
    (filter #(= "athing comtr" (-> % :attrs :class)))
    (map read-item)
    (remove nil?)
    (reduce concat-items [])))

(defn )

(defn main [page-id exclude-list include-list]
  (let [items (->> page-id
                   retrieve-all-pages
                   (map items)
                   (apply concat)
                   (apply str))]
    (apply str ["<body><table>" items "</table></body>"])))