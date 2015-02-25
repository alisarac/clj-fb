(ns clj-fb.core
  (:require [clj-fb.realtime :as rt]
            [compojure.core :refer :all]
            [cheshire.core :as json]))

(defn user-uninstalled
  "Placeholder for uninstall function"
  [facebook-id] 
  facebook-id) ; TODO make it modular so users can do whatever they needed 

;; function to process updated user entry
(defn process-user [{:keys [params status headers body error]}]
  (let [body (json/parse-string body)]
    {:body body})) 

;; function to process updated permissions entry
(defn process-permissions [{:keys [params status headers body error]}]
  (let [body (json/parse-string body)]
    {:body body}))

;; function to process updated payment entry
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
     :completed? completed?}))

(defn wrap-processors [req]
  (assoc req :processors {:payments process-payment
                          :user process-user
                          :permissions process-permissions}))

;; Example
(defroutes facebook-routes
  (GET "/realtime-updates" req rt/hub-challenge-handler)
  (POST "/realtime-updates" req
        (str (rt/realtime-updates-handler (wrap-processors req))))
  (POST "/uninstall" [signed_request]
        (str (rt/uninstall-handler signed_request user-uninstalled))))
