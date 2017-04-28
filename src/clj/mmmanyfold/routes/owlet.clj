(ns mmmanyfold.routes.owlet
  (:require [compojure.core :refer [defroutes GET]]
            [ring.util.http-response :refer [ok not-found]]
            [compojure.api.sweet :refer [context]]
            [org.httpkit.client :as http]
            [cheshire.core :as json]))

(defonce OWLET-DEFAULT-SPACE-ID
         (System/getenv "OWLET_CONTENTFUL_DEFAULT_SPACE_ID"))

(defonce OWLET-CONTENTFUL-MANAGEMENT-AUTH-TOKEN
         (System/getenv "OWLET_CONTENTFUL_MANAGEMENT_AUTH_TOKEN"))

(defonce OWLET-ACTIVITIES-2-CONTENTFUL-DELIVERY-AUTH-TOKEN
         (System/getenv "OWLET_ACTIVITIES_2_CONTENTFUL_DELIVERY_AUTH_TOKEN"))

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
        _space-id_ (or space-id OWLET-DEFAULT-SPACE-ID)
        opts1 {:headers {"Authorization" (str "Bearer " OWLET-CONTENTFUL-MANAGEMENT-AUTH-TOKEN)}}
        opts2 {:headers {"Authorization" (str "Bearer " OWLET-ACTIVITIES-2-CONTENTFUL-DELIVERY-AUTH-TOKEN)}}]
    (let [{:keys [status body]}
          @(http/get (format "https://cdn.contentful.com/spaces/%1s/entries?" _space-id_) opts2)
          metadata (get-activity-metadata _space-id_ opts1)]
      (if (= status 200)
        (ok {:metadata (process-metadata (:body @metadata))
             :entries (json/parse-string body true)})
        (not-found status)))))

(defroutes owlet-routes
           (context "/api" []
             (context "/content" []
               (GET "/space" {params :params} handle-get-all-entries-for-given-user-or-space))))

