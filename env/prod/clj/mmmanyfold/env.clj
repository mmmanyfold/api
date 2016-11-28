(ns mmmanyfold.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[mmmanyfold started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[mmmanyfold has shut down successfully]=-"))
   :middleware identity})
