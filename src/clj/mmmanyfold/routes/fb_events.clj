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

(def ac {:access-token (or (env :access-token) (System/getenv "PICTURE_ROOM_FB_ACCESS_TOKEN"))})

(defn async-get [url results-chan]
  (http/get (str "https://graph.facebook.com/v2.5/" url "/picture")
            {:oauth-token  (:access-token ac)
             :query-params {:redirect 0
                            :type     "large"}}
            #(go (>! results-chan
                     (json/parse-string (:body %) true)))))

(defn get-large-format-pics [ids]
  (let [c (chan)
        res (atom [])]
    ;; fetch>!
    (doseq [id ids]
      (async-get id c))
    ;; gather!
    (doseq [_ ids]
      (swap! res conj (<!! c)))
    @res))

(defn get-fb-events []
  (let [events-request (with-facebook-auth ac (client/get [:pictureroomnyc :events] {:extract :data}))
        flatten-pic-urls (mapv #(:id %) events-request)
        event-pic-urls-request (get-large-format-pics flatten-pic-urls)
        join-requests (map (fn [e]
                             (let [index (.indexOf flatten-pic-urls (:id e))
                                   pic-url (get-in (get event-pic-urls-request index)
                                                   [:data :url])]
                               (assoc e :picture_url pic-url))) events-request)]
    (response {:data join-requests})))

(defroutes fb-event-routes
           (GET "/fb-events" [] (get-fb-events)))