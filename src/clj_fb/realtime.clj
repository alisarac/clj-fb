(ns clj-fb.realtime
  (:require [compojure.core :refer :all]
            [environ.core :refer [env]]
            [ring.util.response :as r]
            [cheshire.core :as json]
            [clj-fb.graph-api :as api]
            [clojure.string :as s])
  (:import (org.apache.commons.codec.binary Base64)
           (javax.crypto Mac)
           (javax.crypto.spec SecretKeySpec)))

(def verify-token (env :verify-token)) ; should be same as in facebook developer

(def secret (env :secret)) ; obtained from facebook developer console

(defn hub-challenge [req]
  (let [params (:params req)
        hub-mode (get params "hub.mode" nil)
        hub-challenge (get params "hub.challenge" nil)
        hub-verify-token (get params "hub.verify_token" nil)]
    (if (and (= hub-mode "subscribe")
             (= hub-verify-token verify-token))
      (r/response hub-challenge)
      (r/response nil))))

(defn wrap-text-response [handler]
  (fn [request]
    (let [response (handler request)]
      (r/header response "Content-Type" "text/plain; charset=utf-8"))))

(defn hub-challenge-handler [req]
  (wrap-text-response hub-challenge-handler))

;; Implementation detail about verifying signed request
(defn secret-key-inst [key mac]
  (SecretKeySpec. (.getBytes key) (.getAlgorithm mac)))

(defn sign 
  "Returns the signature of a string with a given key, using a SHA-256 HMAC."
  [key string]
  (let [mac (Mac/getInstance "HMACSHA256")
        secret-key (secret-key-inst key mac)]
    (-> (doto mac
          (.init secret-key)
          (.update (.getBytes string)))
        .doFinal)))

(defn bytes->hex-string 
  "Convert bytes to a String"
  [bytes]
  (apply str (map #(format "%x" %) bytes)))

(defn parse-signed-request 
  "Verifies and parses signed request from facebook. 
  If not signed request is not verified returns nil."
  [signed-req]
  (let [signed-req (s/replace signed-req #"-" "+")
        signed-req (s/replace signed-req #"_" "/")
        [encoded-sig payload] (s/split signed-req #"\.")
        base64 (Base64. true)
        sig (.decode base64 (.getBytes encoded-sig "UTF-8"))
        signed-payload (sign secret payload)]
    (when (= (bytes->hex-string sig) (bytes->hex-string signed-payload))
      (json/parse-string (String. (.decode base64 (.getBytes payload "UTF-8")))))))

(defn entry-handler
  "Gets details of update from id of entry"
  [entry f] 
  (api/get-graph-info (:id entry) f))

(defn realtime-updates-handler 
  "Main realtime updates handler function to delegate 
  updates to user defined functions in processors"
  [{:keys [params body error processors]}]
  (let [object (:object params)
        entries (:entry params)]
    (doseq [entry entries]
      (entry-handler entry (get processors (key object) identity)))))

(defn uninstall-handler 
  "Main uninstall handler function to delegate 
  uninstalled user information to user created function"
  [signed-request f]
  (when-let [parsed-request (parse-signed-request signed-request)]
    (f (parsed-request "user_id"))))



