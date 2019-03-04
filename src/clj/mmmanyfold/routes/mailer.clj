(ns mmmanyfold.routes.mailer
  (:require [mailgun.mail :as mail]
            [compojure.core :refer [defroutes POST]]
            [ring.util.http-response :refer :all]))

(def creds {:key    (System/getenv "MMM_MAILGUN_API_KEY")
            :domain "playgroundcoffeeshop.com"})

(defn handle-posting-via-mailgun [req]
  (let [{:keys [body subject to from domain]} (:params req)
        mail-transact!
        (mail/send-mail (if domain
                          (assoc creds :domain domain) creds)
                        {:from    from
                         :to      to
                         :subject subject
                         :html    body})]
    (if (= (:status mail-transact!) 200)
      (ok mail-transact!)
      (internal-server-error mail-transact!))))

(defroutes mailer-routes
           (POST "/mail" {params :params} handle-posting-via-mailgun))
