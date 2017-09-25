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
         (let [{productImages :productImages} (json/parse-string (:body %) true)
               src-urls (map :src productImages)]
           (>! results-chan {id (vec src-urls)}))))
    :featured
    (http/get
      (format "https://api.shoplightspeed.com/us/products/%s.json" id)
      basic-auth-header
      #(go
         (let [{product :product} (json/parse-string (:body %) true)]
            (>! results-chan product))))
    :brands
    (http/get
      (format "https://api.shoplightspeed.com/us/brands/%s.json" id)
      basic-auth-header
      #(go
         (let [{brand :brand} (json/parse-string (:body %) true)]
              (>! results-chan (select-keys brand [:id :title])))))
    :prices
    (http/get
      (format "https://api.shoplightspeed.com/us/variants.json?product=%s" id)
      basic-auth-header
      #(go
         (let [{variants :variants} (json/parse-string (:body %) true)
               priceExcl (map :priceExcl variants)
               sortedPrices (sort priceExcl)
               priceRange ((juxt first last) sortedPrices)]
           (>! results-chan {id (vec (distinct priceRange))}))))))

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


;; for getting featured products

(defn get-featured-products [_]
  (let [{:keys [status body]} @(http/get "https://api.shoplightspeed.com/us/tags/products.json" basic-auth-header)]
    (if (= 200 status)
      (let [{tagsProducts :tagsProducts} (json/parse-string body true)
            featured-tag-id 150470
            featured-tag-data (filter #(= (-> % :tag :resource :id) featured-tag-id) tagsProducts)
            featured-product-ids (map #(-> % :product :resource :id) featured-tag-data)
            featured-products (get-product-data :featured featured-product-ids)
            ;; filter visible products
            featured-visible (filter #(:isVisible %) featured-products)
            ;; retrieve brand data
            brand-ids (distinct (map #(-> % :brand :resource :id) featured-visible))
            brand-data (into (hash-map) (map #(hash-map (:id %) (:title %)) (get-product-data :brands brand-ids)))
            featured-visible-ids (map :id featured-visible)
            product-price-range (map #(hash-map (first (keys %)) (hash-map :price-range (first (vals %))))
                                     (sort-by first (get-product-data :prices featured-visible-ids)))
            product-images (map #(hash-map (first (keys %)) (hash-map :images (first (vals %))))
                                (sort-by first (get-product-data :images featured-visible-ids)))
            select-product-data (sort-by first
                                  (map #(hash-map (:id %) (dissoc % :id))
                                     (map #(select-keys % [:id :url :title :brand]) featured-visible)))
            product-with-brand (map (fn [product]
                                        (let [product-id (first (keys product))
                                              product-brand-id (get-in (first (vals product)) [:brand :resource :id])
                                              product-brand-title (get brand-data product-brand-id)
                                              product (dissoc (first (vals product)) :brand)]
                                          (hash-map product-id (assoc product :brand-title product-brand-title))))
                                 select-product-data)
            final-product-data (map #(merge-with into %1 %2 %3) product-price-range product-images product-with-brand)]
        (ok (map (comp first vals) final-product-data)))
      (bad-request "Unable to retrieve product tags"))))


(defroutes lightspeed-ecom-routes
  (context "/lightspeed-ecom" []
    (context "/products" []
      (GET "/featured" {params :params} get-featured-products)
      (GET "/images" {params :params} multi-handle-product-request)
      (GET "/price-range" {params :params} multi-handle-product-request))))
