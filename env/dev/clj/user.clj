(ns user
  (:require [mount.core :as mount]
            mmmanyfold.core))

(defn start []
  (mount/start-without #'mmmanyfold.core/repl-server))

(defn stop []
  (mount/stop-except #'mmmanyfold.core/repl-server))

(defn restart []
  (stop)
  (start))


