(ns mmmanyfold.routes.fb-events
  (:require
    [compojure.core :refer [defroutes GET]]
    [environ.core :refer [env]]
    [clj-facebook-graph.auth :refer [with-facebook-auth]]
    [clj-facebook-graph [client :as client]]
    [ring.util.response :refer [response]]
    [org.httpkit.client :as http]
    [clojure.core.async :refer [>! <! >!! <!! go chan]]
    [cheshire.core :as json]))

(def access-token {:access-token (or (env :access-token) (System/getenv "PICTURE_ROOM_FB_ACCESS_TOKEN"))})

(defn async-get [event results-chan]
      (http/get (str "https://graph.facebook.com/v2.5/" (event :id) "/picture")
            {:oauth-token  (:access-token access-token)
             :query-params {:redirect 0
                            :type     "large"}}
            (fn [res]
              (go (>! results-chan
                      (let [json (json/parse-string (:body res) true)
                            picture_url (-> json :data :url)]
                           (assoc event :picture_url picture_url)))))))

(defn get-large-format-pics [events]
  (let [c (chan)
        res (atom [])]
    ;; fetch>!
    (doseq [e events]
      (async-get e c))
    ;; gather!
    (doseq [_ events]
      (swap! res conj (<!! c)))
    @res))

(defn get-fb-events []
  (let [events-request (with-facebook-auth access-token (client/get [:pictureroomnyc :events] {:extract :data}))
        event-pic-urls-request (get-large-format-pics events-request)]
    (response {:data event-pic-urls-request})))

(defroutes fb-event-routes
           (GET "/fb-events" [] (get-fb-events)))