(ns mmmanyfold.routes.mailer
  (:require [mailgun.mail :as mail]
            [mailgun.util :refer [to-file]]
            [compojure.core :refer [defroutes POST]]
            [ring.util.http-response :refer :all]
            [clojure.java.io :as io]))

;; TODO: get :domain from params map as well
(def creds {:key    (System/getenv "PLAYGROUND_MAILGUN_API_KEY")
            :domain "playgroundcoffeeshop.com"})

(defn handle-posting-via-mailgun [req]
  (let [{:keys [body subject to from]} (:params req)
        opts {:from    from
              :to      to
              :subject subject
              :html    body}]
    (if-let [attachment (get-in req [:params :attachment])]
      (let [temp-csv (java.io.File/createTempFile "temp" ".csv")
            file-path (.getAbsolutePath temp-csv)
            _ (spit temp-csv attachment)
            opts {:attachment (to-file [file-path])
                  :from       from
                  :to         to
                  :subject    subject
                  :html       body}
            mail-transact! (mail/send-mail creds opts)]
        (if (= (:status mail-transact!) 200)
          (ok mail-transact!)
          (internal-server-error mail-transact!)))
      (let [mail-transact! (mail/send-mail creds opts)]
        (if (= (:status mail-transact!) 200)
          (ok mail-transact!)
          (internal-server-error mail-transact!))))))

(defroutes mailer-routes
           (POST "/mail" {params :params} handle-posting-via-mailgun))
