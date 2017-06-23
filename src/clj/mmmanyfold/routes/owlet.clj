(ns mmmanyfold.routes.owlet
  (:require [compojure.core :refer [defroutes GET POST PUT]]
            [selmer.parser :refer [render-file]]
            [selmer.filters :refer [add-filter!]]
            [ring.util.http-response :refer [ok not-found internal-server-error]]
            [compojure.api.sweet :refer [context]]
            [org.httpkit.client :as http]
            [mailgun.mail :as mail]
            [cheshire.core :as json]
            [camel-snake-kebab.core :refer [->kebab-case]]))

(def creds {:key    (System/getenv "MMM_MAILGUN_API_KEY")
            :domain "playgroundcoffeeshop.com"})

(defonce OWLET-ACTIVITIES-3-MANAGEMENT-AUTH-TOKEN
         (System/getenv "OWLET_ACTIVITIES_3_MANAGEMENT_AUTH_TOKEN"))

(defonce OWLET-ACTIVITIES-3-DELIVERY-AUTH-TOKEN
         (System/getenv "OWLET_ACTIVITIES_3_DELIVERY_AUTH_TOKEN"))

(defonce subscribers-endpoint "https://owlet-users.firebaseio.com/subscribers.json")

(add-filter! :kebab #(->kebab-case %))

(defn- get-activity-metadata
  "GET all branches in Activity model for owlet-activities-2 space"
  [space-id headers]
  (http/get (format "https://api.contentful.com/spaces/%1s/content_types" space-id) headers))

(defn- get-entry-by-id [space-id entry-id]
  (http/get (format "https://cdn.contentful.com/spaces/%1s/entries/%2s" space-id entry-id)
    {:headers {"Authorization" (str "Bearer " OWLET-ACTIVITIES-3-DELIVERY-AUTH-TOKEN)}}))

(defn- get-asset-by-id [space-id asset-id]
  (http/get (format "https://cdn.contentful.com/spaces/%1s/assets/%2s" space-id asset-id)
    {:headers {"Authorization" (str "Bearer " OWLET-ACTIVITIES-3-DELIVERY-AUTH-TOKEN)}}))

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

(defn- filter-entries [content-type items]
   (filter #(= content-type
               (-> % (get-in [:sys :contentType :sys :id])))
     items))

(defn- image-by-id
  "Maps image IDs to associated URL, width, and height."
  [assets]
  (->> assets
    (map
      (juxt
        (comp :id :sys)
        #(hash-map
           :url (get-in % [:fields :file :url])
           :w   (get-in % [:fields :file :details :image :width])
           :h   (get-in % [:fields :file :details :image :height]))))
    (into {})))

(defn- keywordize-name [name]
  (-> name ->kebab-case keyword))

(def remove-nil (partial remove nil?))

(defn- process-activity [activity platforms assets]
  (-> activity
    ; Adds :platform data using :platformRef
    (assoc-in [:fields :platform]
              (some #(when (= (get-in activity [:fields :platformRef :sys :id])
                              (get-in % [:sys :id]))
                           (hash-map :name (get-in % [:fields :name])
                                     :search-name (str (->kebab-case (get-in % [:fields :name])))
                                     :color (get-in % [:fields :color])))
                platforms))
    ; Adds preview img. URL at [.. :sys :url]
    (update-in [:fields :preview :sys]
               (fn [{id :id :as sys}]
                 (assoc sys
                   :url
                   (get-in (image-by-id assets) [id :url]))))
    ; Adds :image-gallery-items
    (assoc-in [:fields :image-gallery-items]
              (->> (get-in activity [:fields :imageGallery])
                   (map (comp :id :sys))        ; Gallery image ids.
                   (mapv (image-by-id assets))))
    ; Add :skill-set
    (assoc-in [:fields :skill-set] (or (some->> activity
                                            :fields
                                            :skills
                                            remove-nil
                                            seq          ; some->> gives nil if empty
                                            (map keywordize-name)
                                            set)
                                       activity))))

(defn- process-activities
  [activities platforms assets]
  (for [activity activities]
    (process-activity activity platforms assets)))


(defn handle-get-all-entries-for-given-space

  "asynchronously GET all entries for given space
  optionally pass library-view=true param to get all entries for given space"

  [req]

  (let [{:keys [space-id]} (:params req)
        opts1 {:headers {"Authorization" (str "Bearer " OWLET-ACTIVITIES-3-MANAGEMENT-AUTH-TOKEN)}}
        opts2 {:headers {"Authorization" (str "Bearer " OWLET-ACTIVITIES-3-DELIVERY-AUTH-TOKEN)}}]
    (let [{:keys [status body]}
          @(http/get (format "https://cdn.contentful.com/spaces/%1s/entries?" space-id) opts2)
          metadata (get-activity-metadata space-id opts1)]
      (if (= status 200)
        (let [entries (json/parse-string body true)
              assets (get-in entries [:includes :Asset])
              platforms (filter-entries "platform" (:items entries))
              activities (filter-entries "activity" (:items entries))]
          (ok {:metadata (process-metadata (:body @metadata))
               :activities (process-activities activities platforms assets)
               :platforms platforms}))
        (not-found status)))))

(defn- compose-new-activity-email
  "Pluck relevant keys from activity payload and return { subject, body }"
  [activity]
  (let [id (-> activity :sys :id)
        title (-> activity :fields :title :en-US)
        author (-> activity :fields :author :en-US)
        image-url (-> activity :fields :preview :sys :url)
        platform-color (-> activity :fields :platform :color)
        platform-name (-> activity :fields :platform :name)
        skills (-> activity :fields :skills :en-US)
        description (-> activity :fields :summary :en-US)
        subject (format "New Owlet Activity Published: %s by %s" title author)
        url (format "http://owlet.codefordenver.org/#/activity/#!%s" id)
        html (render-file "public/activity-email.html" {:activity-id id
                                                        :activity-image-url image-url
                                                        :activity-title title
                                                        :platform-color platform-color
                                                        :platform-name platform-name
                                                        :activity-description description
                                                        :skill-names skills})]
    (hash-map :subject subject
              :html html)))

(defn handle-activity-publish
  "Sends email to list of subscribers"
  [req]
  (let [payload (:params req)
        is-new-activity?
        (and (= "activity" (get-in payload [:sys :contentType :sys :id]))
             (= 1 (get-in payload [:sys :revision])))]
    (if is-new-activity?
      (let [{:keys [status body]} @(http/get subscribers-endpoint)]
        (if (= 200 status)
          (let [json (json/parse-string body true)
                coll (remove nil? json)
                subscribers (clojure.string/join "," coll)]
            (let [space-id (get-in payload [:sys :space :sys :id])
                  asset-id (get-in payload [:fields :preview :en-US :sys :id])
                  {:keys [status body]} @(get-asset-by-id space-id asset-id)]
              (if (= 200 status)
                (let [body (json/parse-string body true)
                      asset-url (get-in body [:fields :file :url])
                      payload
                      (-> payload
                          (assoc-in [:fields :preview :sys :url] asset-url))]
                  (let [entry-id (get-in payload [:fields :platformRef :en-US :sys :id])
                        {:keys [status body]} @(get-entry-by-id space-id entry-id)]
                    (if (= 200 status)
                      (let [body (json/parse-string body true)
                            platform-name (get-in body [:fields :name])
                            platform-color (get-in body [:fields :color])
                            payload
                            (-> payload
                              (assoc-in [:fields :platform :name] platform-name)
                              (assoc-in [:fields :platform :color] platform-color))]
                        (let [{:keys [subject html]} (compose-new-activity-email payload)
                              mail-transact!
                              (mail/send-mail creds
                                              {:from    "owlet@mmmanyfold.com"
                                               :to      "owlet@mmmanyfold.com"
                                               :bcc     subscribers
                                               :subject subject
                                               :html    html})]
                          (if (= (:status mail-transact!) 200)
                            (ok "Emailed Subscribers Successfully.")
                            (internal-server-error mail-transact!))))
                      (internal-server-error status))))
                (internal-server-error status))))
          (internal-server-error status)))
      (ok "Not a new activity."))))

(defn handle-activity-subscribe

  "handles new subscription request
   -checks list of subs b4 adding to list; ie no duplicates"

  [req]

  (let [email (-> req :params :email)
        {:keys [status body]} @(http/get subscribers-endpoint)]
    (let [json (json/parse-string body true)
          coll (remove nil? json)]
      (if (= status 200)
        (if (some #{email} coll)
          (ok "Already Subscribed.")
          (let [{:keys [status body]}
                @(http/put subscribers-endpoint
                           {:body (json/encode (conj coll email))})]
            (if (= status 200)
              (ok "Subscribed.")
              (internal-server-error status))))
        (internal-server-error status)))))

(defn handle-activity-unsubscribe
  "handles unsubscribe request"
  [req]
  (let [email (-> req :params :email)
        {:keys [status body]} @(http/get subscribers-endpoint)]
    (if (= status 200)
      (let [json (json/parse-string body true)
            coll (remove nil? json)
            removed (remove #{email} coll)]
        (let [{:keys [status body]}
              @(http/put subscribers-endpoint {:body (json/encode removed)})]
          (if (= status 200)
            (ok (format "Email: %s successfully unsubscribed." email))
            (internal-server-error status)))))))

(defroutes owlet-routes
           (context "/webhook" []
             (context "/content" []
               (POST "/email" {params :params} handle-activity-publish)
               (PUT "/unsubscribe" {params :params} handle-activity-unsubscribe)
               (PUT "/subscribe" {params :params} handle-activity-subscribe)))
           (context "/api" []
             (context "/content" []
               (GET "/space" {params :params} handle-get-all-entries-for-given-space))))
