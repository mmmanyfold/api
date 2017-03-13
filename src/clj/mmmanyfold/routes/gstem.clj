(ns mmmanyfold.routes.gstem
  (:require [compojure.api.sweet :refer [context GET defroutes]]
            [clj-facebook-graph.auth :refer [with-facebook-auth]]
            [clj-facebook-graph [client :as client]]
            [ring.util.http-response :refer [ok]]))

(def ac {:access-token (System/getenv "GSTEM_FB_PAGE_ACCESS_TOKEN")})

(defn get-fb-feed [_]
  (let [feed-request (with-facebook-auth ac (client/get [:me :feed] {:query-params {:limit 10} :extract :data}))]
    (let [filtered (filterv :message feed-request)
          feed-resources (filterv #(re-find #"(?i)#resources?" (:message %)) filtered)]
      (ok feed-resources))))

(defroutes gstem-routes
  (context "/facebook" []
    (GET "/resources" {} get-fb-feed)))