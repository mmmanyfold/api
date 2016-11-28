(ns mmmanyfold.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [mmmanyfold.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[mmmanyfold started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[mmmanyfold has shut down successfully]=-"))
   :middleware wrap-dev})
