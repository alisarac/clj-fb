(ns clj-fb.graph-api
  (:require [org.httpkit.client :as http]
            [environ.core :refer [env]]
            [cheshire.core :as json]
            [clojure.string :as s]))

(def graph-url "https://graph.facebook.com")

(defn get-access-token []
  (str (env :app-id)
       (http/url-encode "|")
       (env :app-secret)))

(defn api-request
  "API request with given params and a result parser function"
  [params f]
  (http/request params f))

(defn create-base-url 
  "Creates base url to call graph-api"
  [url node edges]
  (str (s/join "/" (into [url node] edges))))

(defn wrap-access-token
  "Adds access token to parameters"
  [params]
  (into params {:access_token (get-access-token)}))

(defn get-graph-info 
  "Query facebook graph api, and get result"
  [id f params]
  (api-request {:url (create-base-url graph-url id [])
                :query-params (wrap-access-token params)} f))

(defn parse-result
  "Function example, json body parser"
  [{:keys [status headers body error]}]
  (if error
    error
    (json/parse-string body true)))
