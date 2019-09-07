(ns mmmanyfold.routes.mailer
  (:require [mailgun.mail :as mail]
            [compojure.core :refer [defroutes POST]]
            [ring.util.http-response :refer :all]
            [try-let :refer [try-let]]
            [cheshire.core :as json]))

(def creds {:key    (System/getenv "MMM_MAILGUN_API_KEY")
            :domain "playgroundcoffeeshop.com"})

(defn handle-posting-via-mailgun [req]
  (let [{:keys [body subject to from domain]} (:params req)]
    (try-let [mail-transact!
              (mail/send-mail (if domain
                                (assoc creds :domain domain) creds)
                              {:from    from
                               :to      to
                               :subject subject
                               :html    body})]
             (when (= (:status mail-transact!) 200)
               (ok mail-transact!))
             (catch Exception e
               (let [body (-> e ex-data :body (json/parse-string true))]
                 (case (:message body)
                   "'from' parameter is not a valid address. please check documentation"
                   (internal-server-error "Error: invalid from email")
                   (internal-server-error (:message body))))))))

(defroutes mailer-routes
           (POST "/mail" {params :params} handle-posting-via-mailgun))
