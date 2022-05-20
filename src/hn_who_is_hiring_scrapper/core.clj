(ns hn-who-is-hiring-scrapper.core
  (:require [hickory.core :as h]
            [hickory.render :as r]
            [hickory.select :as s]
            [java.util.regex.Pattern]
            [clj-http.client :as client]
            [clojure.string :as str]))

(def SALARY-RANGES [150 400])
(def BASE-URL "https://news.ycombinator.com/%s")
(def NON-USD-CURRENCIES "£|€|EUR")
(def NON-USD-REGEX (-> "(%s)\\ *\\d{2,3}\\ *K|(%s)\\ *\\d{2,3}\\ *-\\ *\\d{2,3}\\ *K)"
                       (format NON-USD-CURRENCIES NON-USD-CURRENCIES)
                       java.util.regex.Pattern/quote
                       re-pattern))

(defn retrieve-all-pages [page-id]
  (loop [href (format "item?id=%s" page-id)
         contents []]
    (println "=> GET " href)
    (let [raw-content (-> BASE-URL (format href) client/get)
          new-href (->> raw-content
                        :body
                        h/parse
                        h/as-hickory
                        (s/select (s/class "morelink"))
                        first
                        :attrs
                        :href)]
      (if (nil? new-href)
        (conj contents raw-content)
        (recur new-href (conj contents raw-content))))))

(defn read-item [item]
  (when (->> item
             (s/select (s/class "ind"))
             first
             :attrs
             :indent
             Integer/parseInt
             zero?)
    (->> item
         (s/select (s/class "comment"))
         first
         r/hickory-to-html)))

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
    (remove nil?)))

(defn contains-substr? [list item default]
  (if (empty? list)
    default
    (let [item (str/upper-case item)]
      (some true? (for [e list] (str/includes? item e))))))

(defn exclude-item? [list item]
  (contains-substr? list item false))

(defn include-item? [list item]
  (contains-substr? list item true))

(defn max-salary [item]
  (let [item (str/upper-case item)]
    (loop [s (second SALARY-RANGES)]
      (if (str/includes? item (format "%sK" s))
        s
        (if (= (first SALARY-RANGES) s)
          (first SALARY-RANGES)
          (recur (- s 10)))))))

(defn has-salary? [item]
  (->> (-> item str/upper-case (str/replace #"401K" ""))
       (re-seq #"\d{3}K") seq))

(defn non-usd-salary? [item]
  (->> item
       str/upper-case
       (re-seq NON-USD-REGEX)
       seq))

(defn main [page-id exclude-list include-list file-path]
  (let [exclude-list (map str/upper-case exclude-list)
        include-list (map str/upper-case include-list)
        items (->> page-id
                   retrieve-all-pages
                   (map items)
                   (apply concat)
                   (filter has-salary?)
                   (remove non-usd-salary?)
                   (remove #(exclude-item? exclude-list %))
                   (filter #(include-item? include-list %))
                   (sort-by max-salary >)
                   (reduce concat-items [])
                   (apply str))]
    (->> ["<body><table>" items "</table></body>"]
         (apply str)
         (spit file-path))))

