 (ns mmmanyfold.routes.api
   (:require [compojure.api.sweet :refer [context]]
             [compojure.core :refer [defroutes]]
             [mmmanyfold.routes.fb-events :refer [fb-event-routes]]))

(defroutes api-routes
           (context "/api" []
             fb-event-routes))
