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

(defn process-payment [{:keys [params status headers body error]}]
  (let [body (json/parse-string body)
        payment-id (body "id")
        request-id (body "request_id")
        user-id (get-in body ["user" "id"])
        application-id (get-in body ["application" "id"])
        application-namespace (get-in body ["application" "namespace"])
        application-name (get-in body ["application" "name"])
        actions (body "actions")
        refundable-amount (body "refundable_amount")
        items (body "items")
        test? (body "test")
        exchange-rate (body "payout_foreign_exchange_rate")
        completed? (some #(= "completed" (% "status")) actions)]
    {:body body
     :payment-id payment-id
     :request-id request-id
     :user-id user-id
     :application-id application-id
     :application-namespace application-namespace
     :application-name application-name
     :actions actions
     :refundable-amount refundable-amount
     :items items
     :test? test?
     :exchange-rate exchange-rate
     :completed? completed?})) ; TODO make it modular so users can do whatever they needed 

(defn process-user [{:keys [params status headers body error]}]
  (let [body (json/parse-string body)]
    {:body body})) ; TODO make it modular so users can do whatever they needed 

(defn process-permissions [{:keys [params status headers body error]}]
  (let [body (json/parse-string body)]
    {:body body})) ; TODO make it modular so users can do whatever they needed 

(defn entry-handler
  "Gets details of update from id of entry"
  [entry f] 
  (api/get-graph-info (:id entry) f))


(comment defn realtime-updates-handler [{:keys [params body error]}]
  (let [object (:object params)
        entries (:entry params)]
    (condp = object
      "payments" (doseq [entry entries]
                   (entry-handler entry process-payment))
      "user" (doseq [entry entries]
               (entry-handler entry process-user))
      "permissions" (doseq [entry entries]
                      (entry-handler entry process-permissions)))))

(defn wrap-processors [req]
  (assoc req :processors {:payments process-payment
                          :user process-user
                          :permissions process-permissions}))

(defn realtime-updates-handler [{:keys [params body error processors]}]
  (let [object (:object params)
        entries (:entry params)]
    (doseq [entry entries]
      (entry-handler entry (get processors (key object) identity)))))

(defn user-uninstalled
  "Placeholder for uninstall function"
  [facebook-id] 
  facebook-id) ; TODO make it modular so users can do whatever they needed 

(defn uninstall-handler [signed-request f]
  (when-let [parsed-request (parse-signed-request signed-request)]
    (f (parsed-request "user_id"))))


;; Routes should be created by the client, not here
(defroutes facebook-routes
  (GET "/realtime-updates" req
       ((text-response hub-challenge-handler) req))
  (POST "/realtime-updates" req
        (str (realtime-updates-handler (wrap-processors req))))
  (POST "/uninstall" [signed_request]
        (str (uninstall-handler signed_request user-uninstalled))))
