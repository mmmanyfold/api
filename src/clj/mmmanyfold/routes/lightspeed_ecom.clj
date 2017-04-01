(ns mmmanyfold.routes.lightspeed-ecom
  (:require [ring.util.http-response :refer [ok]]
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


(defn handle-product-request
  "gets a list of images urls from each product"
  [req]

  ;; @eemshi
  ;; do this funciton would handle both
  ;; but depending on the path we can do diff stuff...

  (let [product-ids (get-in req [:params :product-ids])
        path-info (:path-info req)
        _ (clojure.pprint/pprint req)]
        ;product-ids-as-vector (read-string product-ids)]
        ;product-images (get-product-images product-ids-as-vector)]
    (ok [])))

(defroutes lightspeed-ecom-routes
  (context "/lightspeed-ecom" []
    (context "/products" []
      (GET "/images" {params :params} handle-product-request)
      (GET "/price-range" {params :params} handle-product-request))))
