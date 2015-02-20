(ns clj-fb.realtime
  (:require [compojure.core :refer :all]
            [environ.core :refer [env]]
            [ring.util.response :as r]
            [cheshire.core :as json])
  (:import (org.apache.commons.codec.binary Base64)
           (javax.crypto Mac)
           (javax.crypto.spec SecretKeySpec)))

(def verify-token (env :verify-token)) ; should be same as in facebook developer

(def secret (env :secret)) ; obtained from facebook developer console

(defn hub-challenge-handler [req]
  (let [params (:params req)
        hub-mode (get params "hub.mode" nil)
        hub-challenge (get params "hub.challenge" nil)
        hub-verify-token (get params "hub.verify_token" nil)]
    (if (and (= hub-mode "subscribe")
             (= hub-verify-token verify-token))
      (r/response hub-challenge)
      (r/response nil))))

(defn text-response [handler]
  (fn [request]
    (let [response (handler request)]
      (r/header response "Content-Type" "text/plain; charset=utf-8"))))

(defn secretKeyInst [key mac]
  (SecretKeySpec. (.getBytes key) (.getAlgorithm mac)))

(defn sign 
  "Returns the signature of a string with a given key, using a SHA-256 HMAC."
  [key string]
  (let [mac (Mac/getInstance "HMACSHA256")
        secretKey (secretKeyInst key mac)]
    (-> (doto mac
          (.init secretKey)
          (.update (.getBytes string)))
        .doFinal)))

(defn toHexString 
  "Convert bytes to a String"
  [bytes]
  (apply str (map #(format "%x" %) bytes)))

(defn parse-signed-request [signed-req]
  (let [signed-req (clojure.string/replace signed-req #"-" "+")
        signed-req (clojure.string/replace signed-req #"_" "/")
        [encoded-sig payload] (clojure.string/split signed-req #"\.")
        base64 (Base64. true)
        sig (.decode base64 (.getBytes encoded-sig "UTF-8"))
        signed-payload (sign secret payload)]
    (when (= (toHexString sig) (toHexString signed-payload))
      (json/parse-string (String. (.decode base64 (.getBytes payload "UTF-8")))))))

(defn update-handler
  "Placeholder for update data"
  [entry] 
  entry)


(defn realtime-updates-handler [{:keys [params body error]}]
  (let [object (:object params)
        entries (:entry params)]
    (condp = object
      "payments" (doseq [entry entries]
                   (update-handler entry))
      "user" (doseq [entry entries]
               (update-handler entry))
      "permissions" (doseq [entry entries]
                      (update-handler entry)))))

(defn user-uninstalled
  "Placeholder for uninstall function"
  [facebook-id] 
  facebook-id)

(defn uninstall-handler [signed-request]
  (when-let [parsed-request (parse-signed-request signed-request)]
    (do
      (user-uninstalled (parsed-request "user_id")))))

(defroutes facebook-routes
  (GET "/realtime-updates" req
       ((text-response hub-challenge-handler) req))
  (POST "/realtime-updates" req
        (str (realtime-updates-handler req)))
  (POST "/uninstall" [signed_request]
        (str (uninstall-handler signed_request))))
