(ns mmmanyfold.routes.aws.polly
  (:require [ring.util.http-response :refer [ok bad-request]]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [amazonica.core :refer [with-credential ex->map]]
            [amazonica.aws.polly :as polly]
            [compojure.api.sweet :refer [context GET POST defroutes]]
            [taoensso.timbre :as timbre
             :refer [log trace debug info warn error fatal report]]))


(defn handle-text-2-speech [req]
  (let [voice (polly/synthesize-speech
                :text "Tesla Passes Ford by Market Cap Before Musk Delivers Model 3"
                :output-format "mp3"
                :voice-id "Ivy")]
    (ok [])))

(defroutes polly-routes
  (context "/aws" []
    (POST "/text2speech" {params :params} handle-text-2-speech)))