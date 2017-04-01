(ns mmmanyfold.routes.lightspeed-ecom
  (:require [ring.util.http-response :refer [ok bad-request]]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.core :refer [read-string]]
            [clojure.core.async :refer [>! <! >!! <!! go chan]]
            [compojure.api.sweet :refer [context GET POST defroutes]]))

(defonce PICTURE_ROOM_LS_API_KEY (System/getenv "PICTURE_ROOM_LS_API_KEY"))

(defonce PICTURE_ROOM_LS_API_SECRET (System/getenv "PICTURE_ROOM_LS_API_SECRET"))

(defn async-get-product-images [id results-chan]
  (http/get (format "https://api.shoplightspeed.com/us/products/%s/images.json" id)
            {:basic-auth [PICTURE_ROOM_LS_API_KEY PICTURE_ROOM_LS_API_SECRET]}
            #(go
               (let [{:keys [productImages]} (json/parse-string (:body %) true)
                     src-urls (map :src productImages)]
                 (>! results-chan {id src-urls})))))

(defn get-product-images [ids]
  (let [c (chan)
        res (atom [])]
    ;; fetch>!
    (doseq [id ids]
      (async-get-product-images id c))
    ;; gather!
    (doseq [_ ids]
      (swap! res conj (<!! c)))
    @res))


(defmulti multi-handle-product-request
  "handle request for product data dependening on the /path"
          (fn [{path-info :path-info}]
            path-info))

(defmethod multi-handle-product-request "/images"
  [req]
  (if-let [product-ids (get-in req [:params :product-ids])]
    (let [product-ids-as-vector (read-string product-ids)
          product-images (get-product-images product-ids-as-vector)]
      (ok product-images))
    (bad-request "missing product-ids query param")))

(defmethod multi-handle-product-request "/price-range"
  [req]

  ;; @eemshi
  ;; in this method we handle the case for prince-range
  ;; we can even format the response on the fly here
  ;; ie produce the range $50-150

  (if-let [product-ids (get-in req [:params :product-ids])]
    (let [product-ids-as-vector (read-string product-ids)
          product-images (get-product-images product-ids-as-vector)]
      (ok product-images))
    (bad-request "missing product-ids query param")))

(defroutes lightspeed-ecom-routes
  (context "/lightspeed-ecom" []
    (context "/products" []
      (GET "/images" {params :params} multi-handle-product-request)
      (GET "/price-range" {params :params} multi-handle-product-request))))
