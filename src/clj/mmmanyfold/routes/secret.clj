(ns mmmanyfold.routes.secret
  (:require [ring.util.http-response :refer [forbidden]]
            [ring.util.response :refer [redirect]]
            [compojure.api.sweet :refer :all]))

(def access-token (System/getenv "ARVID_MILAH_SECRET"))

(defn handle-secret [request]
  (let [token (get-in request [:params :token])]
    (if (= token access-token)
        (ok {:cool "story"})
        (forbidden "Forbidden"))))

(defroutes secret-routes
           (POST "/secret" {params :params} handle-secret))
