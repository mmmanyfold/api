 (ns mmmanyfold.api
   (:require [compojure.api.sweet :refer [context]]
             [compojure.core :refer [defroutes]]
             [mmmanyfold.routes.webhooks :refer [webhooks-routes]]
             [mmmanyfold.routes.lightspeed-ecom :refer [lightspeed-ecom-routes]]
             [mmmanyfold.routes.mailer :refer [mailer-routes]]
             [mmmanyfold.routes.fb-events :refer [fb-event-routes]]
             [mmmanyfold.routes.fb-events :refer [fb-event-routes]]
             [mmmanyfold.routes.secret :refer [secret-routes]]))

(defroutes api-routes
           (context "/api" []
             lightspeed-ecom-routes
             fb-event-routes
             mailer-routes
             secret-routes)
           (context "/webhooks" []
             webhooks-routes))