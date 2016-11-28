(ns mmmanyfold.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [mmmanyfold.routes.services :refer [service-routes]]
            [compojure.route :as route]
            [mmmanyfold.env :refer [defaults]]
            [mount.core :as mount]
            [mmmanyfold.middleware :as middleware]))

(mount/defstate init-app
                :start ((or (:init defaults) identity))
                :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
    #'service-routes
    (route/not-found
      "page not found")))


(defn app [] (middleware/wrap-base #'app-routes))
