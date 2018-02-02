(ns mmmanyfold.routes.mailer
  (:require [mailgun.mail :as mail]
            [mailgun.util :refer to-file]
            [clojure.java.io :as io]
            [compojure.core :refer [defroutes POST]]
            [ring.util.http-response :refer :all]))

;; TODO: get :domain from params map as well
(def creds {:key    (System/getenv "MMM_MAILGUN_API_KEY")
            :domain "mg.codefordenver.org"})

(defn handle-posting-via-mailgun [req]
  (let [{:keys [body subject to from]} (:params req)
        mail-transact!
        (mail/send-mail creds
                        {:from    from
                         :to      to
                         :subject subject
                         :html    body})]
    (if (= (:status mail-transact!) 200)
      (ok mail-transact!)
      (internal-server-error mail-transact!))))

;; TODO: refactor this into handle-posting-via-mailgun (single endpoint)
(defn handle-posting-file-via-mailgun [req]
  (let [{:keys [body subject file to from]} (:params req)
        _ (println (.getAbsolutePath (:tempfile file)))
        mail-transact!
        (mail/send-mail {:key (System/getenv "MMM_MAILGUN_API_KEY")
                         :domain "mg.codefordenver.org"}
                        {:from       from
                         :to         to
                         :attachment (to-file [(.getAbsolutePath (:tempfile file))])
                         :subject    subject
                         :html       body})]
    (if (= (:status mail-transact!) 200)
      (ok mail-transact!)
      (internal-server-error mail-transact!))))

(defroutes mailer-routes
           (POST "/mail" {params :params} handle-posting-via-mailgun)
           (POST "/mail-file" {params :params} handle-posting-file-via-mailgun))
