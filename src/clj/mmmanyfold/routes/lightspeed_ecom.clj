(ns mmmanyfold.routes.lightspeed-ecom
  (:require [ring.util.http-response :refer [ok bad-request]]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [clojure.core :refer [read-string]]
            [clojure.core.async :refer [>! <! >!! <!! go chan]]
            [compojure.api.sweet :refer [context GET POST defroutes]]))


(defonce PICTURE_ROOM_LS_API_KEY (System/getenv "PICTURE_ROOM_LS_API_KEY"))

(defonce PICTURE_ROOM_LS_API_SECRET (System/getenv "PICTURE_ROOM_LS_API_SECRET"))

(def basic-auth-header {:basic-auth [PICTURE_ROOM_LS_API_KEY PICTURE_ROOM_LS_API_SECRET]})

(defn async-get-product-data [id prop results-chan]
  (case prop
    :images
    (http/get
      (format "https://api.shoplightspeed.com/us/products/%s/images.json" id)
      basic-auth-header
      #(go
         (let [{:keys [productImages]} (json/parse-string (:body %) true)
               src-urls (map :src productImages)]
           (>! results-chan {id src-urls}))))
    :featured
    (http/get
      (format "https://api.shoplightspeed.com/us/products/%s/filtervalues.json" id)
      basic-auth-header
      #(go
         (let [{:keys [productFiltervalue]} (json/parse-string (:body %) true)]
           (if-not (empty? productFiltervalue)
             (>! results-chan {id true})
             (>! results-chan {id false})))))
    :prices
    (http/get
      (format "https://api.shoplightspeed.com/us/variants.json?product=%s" id)
      basic-auth-header
      #(go
         (let [{:keys [variants]} (json/parse-string (:body %) true)
               priceExcl (map :priceExcl variants)
               sortedPrices (sort priceExcl)
               priceRange ((juxt first last) sortedPrices)]
           (>! results-chan {id (distinct priceRange)}))))))

(defn get-product-data [prop ids]
  (let [c (chan)
        res (atom [])]
    ;; fetch>!
    (doseq [id ids]
      (async-get-product-data id prop c))
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
          product-images (get-product-data :images product-ids-as-vector)]
      (ok product-images))
    (bad-request "missing product-ids query param")))

(defmethod multi-handle-product-request "/price-range"
  [req]
  (if-let [product-ids (get-in req [:params :product-ids])]
    (let [product-ids-as-vector (read-string product-ids)
          product-images (get-product-data :prices product-ids-as-vector)]
      (ok product-images))
    (bad-request "missing product-ids query param")))


; for getting featured products

(defn get-product-ids-by-page [page]
  (let [{:keys [status body]} @(http/get (format "https://api.shoplightspeed.com/us/products.json?fields=id,isVisible&limit=250&page=%s" page) basic-auth-header)]
    (if (= status 200)
      (let [{products :products} (json/parse-string body true)
            products-visible (filter #(:isVisible %) products)]
        (map :id products-visible))
      status)))

(defn get-product-visible-ids []
  (loop [i 1 all-ids []]
    (let [res (get-product-ids-by-page i)]
      (if-not (empty? res)
        (recur (inc i) (concat all-ids res))
        all-ids))))

(defn get-featured-products [_]
  (let [product-visible-ids (get-product-visible-ids)]
    (ok (get-product-data :featured product-visible-ids))))

(defroutes lightspeed-ecom-routes
  (context "/lightspeed-ecom" []
    (context "/products" []
      (GET "/featured" {params :params} get-featured-products)
      (GET "/images" {params :params} multi-handle-product-request)
      (GET "/price-range" {params :params} multi-handle-product-request))))
