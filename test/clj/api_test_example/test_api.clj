(ns api-test-example.test-api
  (:require
    [api-test-example.handler :as handler]
    [clojure.test :refer [deftest testing is]]
    [reitit.core :as reitit-core]
    [clojure.set :as set]
    [clojure.string :as string]))

(defn- request
  ([method token uri]
   (request method token uri nil))
  ([method token uri body]
   (-> (handler/app
         {:uri            uri
          :request-method method
          :headers        {"auth" token}
          :body-params    body})
       (select-keys [:status :body])
       (update :body read-string))))

(deftest test-resource
  (let [resource-id (-> (request :post 1 "/api/resource" {:content "foo"}) :body :resource-id)]
    (is (= {:status 200 :body {:content "foo"}}
           (request :get 1 (str "/api/resource/" resource-id))))
    (is (= {:status 404 :body {:cause :not-found}}
           (request :get 1 (str "/api/resource/foo"))))
    (is (= {:status 400 :body {:cause :missing-authentication}}
           (request :get nil (str "/api/resource/" resource-id))))
    (is (= {:status 403 :body {:cause :not-authorized}}
           (request :get 2 (str "/api/resource/" resource-id))))
    (is (= {:status 200 :body {}}
           (request :post 1 "/api/authorization" {:resource-id resource-id :token 2})))
    (is (= {:status 200 :body {:content "foo"}}
           (request :get 2 (str "/api/resource/" resource-id))))))

(def next-id (atom 0))

(defn- create-token [ctx]
  (assoc ctx
    :token (swap! next-id inc)))

(defn- create-resource [ctx]
  (assoc ctx
    :resource-id
    (->
      (request :post (:token ctx) "/api/resource" {:content "some content"})
      :body
      :resource-id)))

(def empty-ctx (constantly {}))

(defn with-token []
  (-> (empty-ctx)
      (create-token)))

(defn with-resource []
  (-> (empty-ctx)
      (create-token)
      (create-resource)))

(defn- create-request-path [path ctx]
  (cond-> path
          (:resource-id ctx) (string/replace ":resource-id" (-> ctx :resource-id str))))

(defn test-path
  ([context-fn] (test-path context-fn nil))
  ([context-fn body]
   (fn [path method]
     (let [ctx          (context-fn)
           request-path (create-request-path path ctx)
           request-body (if (and (ifn? body) (not (map? body)))
                          (body ctx)
                          body)]
       (testing "Request without tokens returns 403"
         (is (= {:status 400 :body {:cause :missing-authentication}}
                (request method nil request-path request-body))))
       (testing "Request with tokens returns 200"
         (is (= 200
                (:status
                  (request method (:token ctx) request-path request-body)))))))))

(def test-routes
  {"/api/authorization" {:post   (test-path with-resource
                                            (fn [ctx]
                                              {:resource-id (:resource-id ctx)
                                               :token       (:token ctx)}))
                         :delete (test-path with-resource
                                            (fn [ctx]
                                              {:resource-id (:resource-id ctx)
                                               :token       (:token ctx)}))}
   "/api/resource"      {:post (test-path with-token {:content "some content"})}
   "/api/resource/:resource-id"
                        {:get    (test-path with-resource)
                         :put    (test-path with-resource {:content "updated content"})
                         :delete (test-path with-resource)}})

(defn- route-to-data-row [[route properties]]
  [route (keys (select-keys properties [:get :post :put :patch :delete :options]))])

(deftest all-paths-tested
  (let [route-data      (reitit.core/routes (reitit.core/router handler/api))
        routes          (into {} (map route-to-data-row route-data))
        missing-routes  (apply dissoc routes (keys test-routes))
        extra-routes    (apply dissoc test-routes (keys routes))
        missing-methods (keep
                          (fn [[route methods]]
                            (let [route-methods  (set methods)
                                  tested-methods (set (keys (get test-routes route)))
                                  missing        (set/difference route-methods tested-methods)]
                              (when (and (get test-routes route) (not-empty missing))
                                [route missing])))
                          routes)]

    (is (= 0 (count missing-routes))
        (str "The following routes are not tested: \n - "
             (string/join "\n - " (sort (keys missing-routes)))))
    (is (= 0 (count extra-routes))
        (str "The following paths contain tests, but the API doesn't have paths for them: "
             (keys extra-routes)))

    (is (= 0 (count missing-methods))
        (str "The following routes are not tested: \n - "
             (string/join "\n - " (map (fn [[route method]] (str route ": " method))
                                       missing-methods))))))

(defmacro generate-test [path method]
  `(let [test-name# (symbol (string/replace (str "test-all-paths__" ~path "_" ~method) #"[^a-zA-Z-]" "_"))
         path#      ~path
         method#    ~method]
     `(deftest ~test-name#
        ((-> test-routes (get ~path#) (get ~method#))
         ~path# ~method#))))

(doseq [[path props] test-routes]
  (doseq [[method _] props]
    (eval (generate-test path method))))
