(ns mmmanyfold.routes.webhooks
  (:require [ring.util.http-response :refer [ok forbidden internal-server-error]]
            [ring.util.response :refer [redirect]]
            [org.httpkit.client :as http]
            [compojure.api.sweet :refer [context GET POST]]))

(defn handle-fb-request [request]
  (let [token (get-in request [:params "hub.challenge"])
        request-body (request :body)
        facebook->firebase-url "https://mmmanyfold-fb-page-data.firebaseio.com/feed.json"]
    (if request-body
      (let [{:keys [status body]} @(http/post facebook->firebase-url
                                              {:body request-body})]
        (if (= status 200)
          (ok token)
          (internal-server-error body)))
      (ok token))))

(defroutes webhooks-routes
           (context "/facebook" []
             (GET "/page" {params :params} handle-fb-request)
             (POST "/page" {params :params} handle-fb-request)))