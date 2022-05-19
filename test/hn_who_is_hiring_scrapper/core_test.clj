(ns hn-who-is-hiring-scrapper.core-test
  (:require [clojure.test :refer :all]
            [hn-who-is-hiring-scrapper.core :refer :all]
            [hn-who-is-hiring-scrapper.webpage :as w]))


(deftest test-items-from-content
  (let [results (items w/webpage)]
    (is (= 252 (count results)))))
(deftest test-items-from-empty-content
  (is (thrown? java.lang.AssertionError (items {:status 200 :body ""}))))
(deftest test-items-with-status-error
  (is (thrown? java.lang.AssertionError (items {:status 500 :body "<html></html>"}))))


