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

; TODO: use gstem token
(def ac {:access-token "EAAD79RJOZC4EBAFZCxlz5uBlXF1azljiPrn2qZCoe62fRLrVhzcCbHrUVwOvgW7bQcA9hcHiABBdKZCPAybbCvXh7L5MlWVfaqwORkNQJUsPoTlh6HyxIK5kkLc6y4FOSEsZBLKvAxNMMHWGBlVZCWE7v7r77rjIMZD"})

(defn get-fb-feed [request]
  (let [feed-request (with-facebook-auth ac (client/get [:me :feed] {:query-params {:limit 10} :extract :data}))]
    (let [filtered (filterv :message feed-request)
          feed-resources
          (filterv #(re-find #"(?i)\#resources?" (:message %)) filtered)]
      (ok feed-resources))))

(defroutes gstem-routes
  (context "/facebook" []
    (GET "/resources" {} get-fb-feed)))
