(ns mmmanyfold.routes.owlet
  (:require [compojure.core :refer [defroutes GET POST PUT]]
            [ring.util.http-response :refer [ok not-found internal-server-error]]
            [compojure.api.sweet :refer [context]]
            [org.httpkit.client :as http]
            [mailgun.mail :as mail]
            [cheshire.core :as json]))

(def creds {:key    (System/getenv "PLAYGROUND_MAILGUN_API_KEY")
            :domain "playgroundcoffeeshop.com"})

(defonce OWLET-ACTIVITIES-3-MANAGEMENT-AUTH-TOKEN
         (System/getenv "OWLET_ACTIVITIES_3_MANAGEMENT_AUTH_TOKEN"))

(defonce OWLET-ACTIVITIES-3-DELIVERY-AUTH-TOKEN
         (System/getenv "OWLET_ACTIVITIES_3_DELIVERY_AUTH_TOKEN"))

(defonce subscribers-endpoint "https://owlet-users.firebaseio.com/subscribers.json")

(defn- get-activity-metadata
  "GET all branches in Activity model for owlet-activities-2 space"
  [space-id headers]
  (http/get (format "https://api.contentful.com/spaces/%1s/content_types" space-id) headers))

(defn- process-metadata
  [metadata]
  (let [body (json/parse-string metadata true)
        items (body :items)
        activity-model (some #(when (= (:name %) "Activity") %) items)
        activity-model-fields (:fields activity-model)
        pluck-prop (fn [prop]
                     (-> (get-in (some #(when (= (:id %) prop) %) activity-model-fields)
                                 [:items :validations])
                         first
                         :in))
        skills (pluck-prop "skills")
        branches (pluck-prop "branch")]
    {:skills   skills
     :branches branches}))

(defn handle-get-all-entries-for-given-user-or-space

  "asynchronously GET all entries for given user or space
  optionally pass library-view=true param to get all entries for given space"

  [req]

  (let [{:keys [space-id]} (:params req)
        opts1 {:headers {"Authorization" (str "Bearer " OWLET-ACTIVITIES-3-MANAGEMENT-AUTH-TOKEN)}}
        opts2 {:headers {"Authorization" (str "Bearer " OWLET-ACTIVITIES-3-DELIVERY-AUTH-TOKEN)}}]
    (let [{:keys [status body]}
          @(http/get (format "https://cdn.contentful.com/spaces/%1s/entries?" space-id) opts2)
          metadata (get-activity-metadata space-id opts1)]
      (if (= status 200)
        (ok {:metadata (process-metadata (:body @metadata))
             :entries  (json/parse-string body true)})
        (not-found status)))))

(defn- compose-new-activity-email
  "Pluck relevant keys from activity payload and return { subject, body }"
  [activity]
  (let [id (-> activity :sys :id)
        title (-> activity :fields :title :en-US)
        author (-> activity :fields :author :en-US)
        branch (-> activity :fields :branch :en-US first)
        subject (format "New Owlet Activity Published: %s by %s" title author)
        url (format "http://owlet.codefordenver.org/#/activity/#!%s" id)
        html (format "<a href='%s'>%s</a> (%s)" url title branch)]
    (hash-map :subject subject
              :html html)))

(defn handle-activity-publish
  "Sends email to list fo subscribers"
  [req]
  (let [activity (:params req)
        {:keys [status body]} @(http/get subscribers-endpoint)]
    (if (= 200 status)
      (let [json (json/parse-string body true)
            json (remove nil? json)
            subscribers (clojure.string/join "," json)]
        (let [{:keys [subject html]} (compose-new-activity-email activity)
              mail-transact!
              (mail/send-mail creds
                              {:from    "owlet@mmmanyfold.com"
                               :to      "owlet@mmmanyfold.com"
                               :bcc     subscribers
                               :subject subject
                               :html    html})]
          (if (= (:status mail-transact!) 200)
            (ok "Emailed Subscribers successfully.")
            (internal-server-error mail-transact!)))))))

;; TODO: check list of subs b4 adding to list; ie no duplicates

(defn handle-activity-subscribe
  "handles new subscription request"
  [req]
  (let [{:keys [status body]}
        @(http/put subscribers-endpoint {:body (json/encode {})})]))


;; TODO: add unsubscribe handler

(defn handle-unsubscribe
  "handles unsubscribe request"
  [req])

(defroutes owlet-routes
           (context "/webhook" []
             (context "/content" []
               (POST "/subscribe" {params :params} handle-activity-publish)
               (PUT "/unsubscribe" {params :params} handle-unsubscribe)
               (PUT "/subscribe" {params :params} handle-activity-subscribe)))
           (context "/api" []
             (context "/content" []
               (GET "/space" {params :params} handle-get-all-entries-for-given-user-or-space))))