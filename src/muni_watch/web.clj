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
            [ring.util.codec :as ring-codec]
            [cemerick.drawbridge :as drawbridge]
            [environ.core :refer [env]]
            [clj-http.client :as client]
            [hiccup.core :refer :all]
            [clojure.data.json :as json]))

(defn get-stops []
  (-> (client/get "http://stormy-fjord-3536.herokuapp.com/get-stops")
      :body
      json/read-str))

(defn get-stop-predictions [stop-name]
  (-> (client/get
       "http://stormy-fjord-3536.herokuapp.com/get-predictions-for-stop"
       {:query-params {"name" (ring-codec/url-encode stop-name)}})
      :body
      json/read-str))

(defn show-stops []
  (html [:h1 "Yelpy Stops"]
        [:ul (for [stop (get-stops)]
               [:li [:a {:href (str "watch/" (stop "name"))} (stop "name")]])])
  )

(defn show-stop-predictions [stop-name]
  (let [predictions (get-stop-predictions stop-name)]
    (html [:div [:h1 "Predictions for " (get-in (first predictions) ["attrs" "stoptitle"])]
           (for [route predictions]
             [:div [:h2 (get-in route ["attrs" "routetitle"])]
              (for [direction (route "content")]
                [:div
                 [:h3 (get-in direction ["attrs" "title"])]
                 [:ul (for [prediction (direction "content")] 
                        [:li (get-in prediction ["attrs" "minutes"]) " minutes"])]])])])))

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
  (GET "/watch/:stop-name" [stop-name]
       (show-stop-predictions stop-name))
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

