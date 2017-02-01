(ns mmmanyfold.routes.webhooks
  (:require [ring.util.http-response :refer [ok forbidden internal-server-error]]
            [ring.util.response :refer [redirect]]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [cuerdas.core :as cuerdas]
            [compojure.api.sweet :refer [context GET POST defroutes]]))

(defonce indico-api-key (System/getenv "INDICO_API_KEY"))

(defonce default-facebook->firebase-url "https://mmmanyfold-fb-page-data.firebaseio.com/feed.json")

(defonce indico-sentiment-endpoint "https://apiv2.indico.io/sentiment")

(defn verb-parser
  "determine if feed-event is remove vs edit vs add vs remove"
  [event]
  (let [verb (-> event :entry first :changes first :value :verb)]
    verb))

(defn comment-parser
  "determine if feed-event is remove vs edit vs add vs remove"
  [event]
  (let [comment (-> event :entry first :changes first :value :message)]
    comment))

(defn analyze-sentiment
  "send request to indico API for sentiment analysis"
  [text]
  (http/post indico-sentiment-endpoint
             {:body    (json/encode {:data text})
              :headers {"X-ApiKey" indico-api-key}}))

(defn handle-fb-request [request]
  (let [token (get-in request [:params "hub.challenge"])
        body (request :body)]
    (if body
      (let [request-body (slurp (request :body))
            event (json/decode request-body true)
            verb (verb-parser event)
            message (case verb
                      "add" (comment-parser event)
                      "comment" (comment-parser event)
                      "default" "")]
        (let [{:keys [status body]} @(analyze-sentiment message)
              sentiment (-> body (json/decode true) :results)]
          (if (= status 200)
            (let [{:keys [status body]} @(http/delete default-facebook->firebase-url)]
              (if (= status 200) ;; delete is successful
                (let [{:keys [status body]} @(http/post default-facebook->firebase-url
                                                        {:body (json/encode {:sentiment sentiment})})]
                  (if (= status 200)
                    (ok token) ;; let FB know that request was processed
                    (internal-server-error body)))
                (internal-server-error body)))
            (internal-server-error body))))
      (ok token))))

(defn handle-latest-request [req]
  (let [{:keys [status body]} @(http/get default-facebook->firebase-url)
        sentiment-value (-> body (json/decode true) first second :sentiment)]
    (if (= status 200)
      (ok sentiment-value)
      (internal-server-error body))))

(defroutes webhooks-routes
  (context "/facebook" []
    (GET "/page" {params :params} handle-fb-request)
    (POST "/page" {params :params} handle-fb-request)
    (context "/page" []
      (GET "/latest" {params :params} handle-latest-request))))