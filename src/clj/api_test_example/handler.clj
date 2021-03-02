(ns api-test-example.handler
  (:require
    [muuntaja.core :as m]
    [reitit.coercion.spec]
    [reitit.ring :as reitit-ring]
    [reitit.ring.coercion :as coercion]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.parameters :as parameters]
    [reitit.ring.spec]
    [reitit.spec]

    [hiccup.page :refer [include-js include-css html5]]
    [config.core :refer [env]]))

(def next-resource-id (atom 1))
(def database (atom {}))
(defn reset-db []
  (reset! next-resource-id 1)
  (reset! database {}))

(defn if-authorized [req resource-id callback]
  (let [auth-id (-> req :headers (get "auth"))]
    (cond
      (not auth-id)
      {:status 400 :body {:cause :missing-authentication}}

      (not (contains? @database resource-id))
      {:status 404 :body {:cause :not-found}}

      (not (contains? (-> @database (get resource-id) :auth-list) auth-id))
      {:status 403 :body {:cause :not-authorized}}

      :else
      (callback))))

(defn loading-page [] (html5 [:head] [:body]))

(defn add-authorization [req]
  (if-authorized
    req
    (-> req :body-params :resource-id)
    (fn []
      (let [resource-id (-> req :body-params :resource-id)
            new-id      (-> req :body-params :token)]
        (swap! database update-in [resource-id :auth-list] #(conj % new-id))
        {:status 200
         :body   {}}))))

(defn remove-authorization [req]
  (if-authorized
    req
    (-> req :body-params :resource-id)
    (fn []
      (let [resource-id (-> req :body-params :resource-id)
            removed-id  (-> req :body-params :token)]
        (swap! database update-in [resource-id :auth-list] #(disj % removed-id))
        {:status 200
         :body   {}}))))

(defn create-resource [req]
  (let [auth-id (-> req :headers (get "auth"))
        content (get (:body-params req) :content)]
    (cond
      (not auth-id)
      {:status 400
       :body   {:cause :missing-authentication}}

      (empty? content)
      {:status 400
       :body   {:cause :invalid-body}}

      :else
      (do
        (let [resource-id (str (swap! next-resource-id inc))]
          (swap! database assoc resource-id
                 {:auth-list #{auth-id}
                  :body      content})
          {:status 200
           :body   {:resource-id resource-id}})))))

(defn read-resource [req]
  (let [resource-id (-> req :path-params :resource-id)]
    (if-authorized
      req
      resource-id
      (fn []
        {:status 200
         :body   {:content (-> @database (get resource-id) :body)}}))))

(defn update-resource [req]
  (let [resource-id (-> req :path-params :resource-id)]
    (if-authorized
      req
      resource-id
      (fn []
        (swap! database assoc-in [resource-id :body] (-> req :body-params :content))
        {:status 200
         :body   {}}))))

(defn delete-resource [req]
  (let [resource-id (-> req :path-params :resource-id)]
    (if-authorized
      req
      resource-id
      (fn []
        (swap! database dissoc resource-id)
        {:status 200
         :body   {}}))))

(def api
  [["/api"
    ["/authorization"
     {:post   add-authorization
      :delete remove-authorization}]
    ["/resource"
     ["" {:post create-resource}]
     ["/:resource-id"
      {:get    read-resource
       :put    update-resource
       :delete delete-resource}]]]])

(def app
  (reitit-ring/ring-handler
    (reitit-ring/router
      api
      {:validate reitit.spec/validate
       :data     {:coercion   reitit.coercion.spec/coercion
                  :muuntaja   m/instance
                  :middleware [;; query-params & form-params
                               parameters/parameters-middleware
                               ;; content-negotiation
                               muuntaja/format-negotiate-middleware
                               ;; encoding response body
                               muuntaja/format-response-middleware
                               ;; decoding request body
                               muuntaja/format-request-middleware
                               ;; coercing response bodys
                               coercion/coerce-response-middleware
                               ;; coercing request parameters
                               coercion/coerce-request-middleware
                               ;; multipart
                               multipart/multipart-middleware
                               (fn [handler]
                                 (fn [req]
                                   (update
                                     (handler req)
                                     :body str)))]}})
    (reitit-ring/routes
      (reitit-ring/create-default-handler))))
