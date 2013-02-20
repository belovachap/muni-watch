(ns muni-watch.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.middleware.stacktrace :as trace]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as cookie]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.basic-authentication :as basic]
            [cemerick.drawbridge :as drawbridge]
            [environ.core :refer [env]]
            [clj-http.client :as client]
            [hiccup.core :refer :all]))

(defn get-stops []
  (:body (client/get
          "http://stormy-fjord-3536.herokuapp.com/get-stops"
          {:as :json})))

(defn get-stop-predictions [stop-tag]
  (:body (client/get
          "http://stormy-fjord-3536.herokuapp.com/get-predictions-for-stop"
          {:query-params {"stop-tag" stop-tag}
           :as :json})))

(defn show-stops []
  (html [:h1 "Yelpy Stops"]
        [:ul (for [stop (get-stops)]
               [:li [:a {:href (str "watch/" (:stopTag stop))} (:title stop)]])])
  )

(defn show-stop-predictions [stop-tag]
  (let [predictions (get-stop-predictions stop-tag)]
    (html [:h1 "Predictions for " (get-in (first predictions) [:attrs :stoptitle])]
          (for [route predictions]
            [:div
             [:h2 (get-in route [:attrs :routetitle])]
             (for [direction (:content route)]
               [:div
                [:h3 (get-in direction [:attrs :title])]
                [:ul (for [prediction (:content direction)]
                       [:li (get-in prediction [:attrs :minutes]) " minutes"])]])]))))

(defn- authenticated? [user pass]
  ;; TODO: heroku config:add REPL_USER=[...] REPL_PASSWORD=[...]
  (= [user pass] [(env :repl-user false) (env :repl-password false)]))

(def ^:private drawbridge
  (-> (drawbridge/ring-handler)
      (session/wrap-session)
      (basic/wrap-basic-authentication authenticated?)))

(defroutes app
  (ANY "/repl" {:as req}
       (drawbridge req))
  (GET "/" []
       (show-stops))
  (GET "/watch/:stop-id" [stop-id]
       (show-stop-predictions stop-id))
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn wrap-error-page [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (slurp (io/resource "500.html"))}))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))
        ;; TODO: heroku config:add SESSION_SECRET=$RANDOM_16_CHARS
        store (cookie/cookie-store {:key (env :session-secret)})]
    (jetty/run-jetty (-> #'app
                         ((if (env :production)
                            wrap-error-page
                            trace/wrap-stacktrace))
                         (site {:session {:store store}}))
                     {:port port :join? false})))

;; For interactive development:
;; (.stop server)
;; (def server (-main))

