(ns mmmanyfold.routes.gstem
  (:require
    [compojure.api.sweet :refer [context GET POST defroutes]]
    [environ.core :refer [env]]
    [clj-facebook-graph.auth :refer [with-facebook-auth]]
    [clj-facebook-graph [client :as client]]
    [ring.util.response :refer [response]]
    [org.httpkit.client :as http]
    [ring.util.http-response :refer [ok]]
    [clojure.core.async :refer [>! <! >!! <!! go chan]]
    [cheshire.core :as json]))

(def ac {:access-token (System/getenv "GSTEM_FB_PAGE_ACCESS_TOKEN")})

(defn get-fb-feed [request]
  (let [feed-request (with-facebook-auth ac (client/get [:me :feed] {:query-params {:limit 10} :extract :data}))]
    (let [filtered (filterv :message feed-request)
          feed-resources
          (filterv #(re-find #"(?i)\#resources?" (:message %)) filtered)]
      (ok feed-resources))))

(defroutes gstem-routes
  (context "/facebook" []
    (GET "/resources" {} get-fb-feed)))