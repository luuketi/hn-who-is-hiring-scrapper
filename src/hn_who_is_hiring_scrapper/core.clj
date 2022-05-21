(ns hn-who-is-hiring-scrapper.core
  (:require [hickory.core :as h]
            [hickory.render :as r]
            [hickory.select :as s]
            [clj-http.client :as client]
            [clojure.string :as str]))

(def SALARY-RANGES [150 400])
(def BASE-URL "https://news.ycombinator.com/%s")
(def NON-USD-CURRENCIES "£|€|EUR")
(def NON-USD-REGEX (-> "(%s)\\ *\\d{2,3}\\ *K|(%s)\\ *\\d{2,3}\\ *-\\ *\\d{2,3}\\ *K"
                       (format NON-USD-CURRENCIES NON-USD-CURRENCIES)
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
             (Integer/parseInt)
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
    (let [item (str/upper-case item)
          list (map str/upper-case list)]
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
          (recur (- s 5)))))))

(defn non-usd-salary? [item]
  (->> item
       str/upper-case
       (re-seq NON-USD-REGEX)
       seq))

(defn salary-between-range? [item]
  (let [min (first SALARY-RANGES)
        max (second SALARY-RANGES)
        no-401 (-> item
                   (str/upper-case)
                   (str/replace #"401K" ""))
        groups (->> no-401
                    (re-seq #"\ *(\d{2,3})\ *K"))
        valid-salary? (->> groups
                           (map (fn [[_ v]] (Integer/parseInt v)))
                           (filter #(and (> % min) (< % max)))
                           seq)]
    (when (seq groups)
      valid-salary?)))


(defn main [page-id exclude-list include-list file-path]
  (let [exclude-list (map str/upper-case exclude-list)
        include-list (map str/upper-case include-list)
        all-items (->> page-id
                       retrieve-all-pages
                       (map items)
                       (apply concat))
        valid-items (->> all-items
                         (remove non-usd-salary?)
                         (remove #(exclude-item? exclude-list %))
                         (filter #(include-item? include-list %))
                         (filter salary-between-range?)
                         (sort-by max-salary >)
                         (reduce concat-items [])
                         (apply str))
        invalid-items (->> all-items
                           (remove (fn [i] (some #(= i %) valid-items)))
                           (reduce concat-items [])
                           (apply str))]
    (->> ["<body><table>"
          valid-items
          "<tr><td><hr width=\"90%\" color=\"red\"></tr></td>"
          invalid-items
          "</table></body>"]
         (apply str)
         (spit file-path))))
