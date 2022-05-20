(ns hn-who-is-hiring-scrapper.core-test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [clj-fakes.core :as f]
            [clj-http.client :as client]
            [hn-who-is-hiring-scrapper.core :refer :all]
            [hn-who-is-hiring-scrapper.webpage :as w]))


(deftest test-items-from-content
  (let [results (items w/webpage)]
    (is (= 248 (count results)))))

(deftest test-items-from-empty-content
  (is (thrown? java.lang.AssertionError (items {:status 200 :body ""}))))

(deftest test-items-with-status-error
  (is (thrown? java.lang.AssertionError (items {:status 500 :body "<html></html>"}))))


(deftest test-read-comment
  (is (= true (str/starts-with? (first (items w/webpage)) "<div class=\"comment\">"))))

(deftest test-concat-results
  )