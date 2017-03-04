 (ns mmmanyfold.api
   (:require [compojure.api.sweet :refer [context]]
             [compojure.core :refer [defroutes]]
             [mmmanyfold.routes.webhooks :refer [webhooks-routes]]
             [mmmanyfold.routes.mailer :refer [mailer-routes]]
             [mmmanyfold.routes.fb-events :refer [fb-event-routes]]
             [mmmanyfold.routes.fb-events :refer [fb-event-routes]]
             [mmmanyfold.routes.secret :refer [secret-routes]]
             [mmmanyfold.routes.gstem :refer [gstem-routes]]))

(defroutes api-routes
           (context "/api" []
             fb-event-routes
             mailer-routes
             secret-routes)
           (context "/gstem" []
             gstem-routes)
           (context "/webhooks" []
             webhooks-routes))
