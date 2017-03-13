(ns mmmanyfold.test.gstem-utils
  (:require [clojure.test :refer :all]
            [clj-facebook-graph [client :as client]]
            [mmmanyfold.routes.gstem :refer [get-fb-feed]]))

(deftest test-regex-portion-of-get-fb-feed-fn

  (testing "filter non #resource(s)"
    (let [fb-request [{:message      "foo"}]]
      (with-redefs-fn {#'client/get (fn [path query-params] fb-request)}
        #(is (= [] (:body (get-fb-feed {})))))))


  (testing "filter multiple words, no match"
    (let [fb-request [{:message      "#foo bar waz"}]]
      (with-redefs-fn {#'client/get (fn [path query-params] fb-request)}
        #(is (= [] (:body (get-fb-feed {})))))))


  (testing "filter multiple words, with match"
    (let [mock-msg "#resource foo bar"
          fb-request [{:message      mock-msg}]]
      (with-redefs-fn {#'client/get (fn [path query-params] fb-request)}
        #(is (= mock-msg (-> (get-fb-feed {})
                             :body
                             first
                             :message))))))


  (testing "CAPITAL LETTERS"
    (let [mock-msg "#RESOURCE foo bar"
          fb-request [{:message      mock-msg}]]
      (with-redefs-fn {#'client/get (fn [path query-params] fb-request)}
        #(is (= mock-msg (-> (get-fb-feed {})
                             :body
                             first
                             :message))))))


  (testing "plural #resource"
    (let [mock-msg "#resources foo bar"
          fb-request [{:message      mock-msg}]]
      (with-redefs-fn {#'client/get (fn [path query-params] fb-request)}
        #(is (= mock-msg (-> (get-fb-feed {})
                             :body
                             first
                             :message)))))))
