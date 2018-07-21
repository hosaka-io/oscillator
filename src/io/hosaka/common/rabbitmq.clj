(ns io.hosaka.common.rabbitmq
  (:require [com.stuartsierra.component :as component]
            [java-time                  :as time]
            [cheshire.generate          :as gen]
            [cheshire.core              :as json]
            [langohr.basic              :as lb]
            [langohr.channel            :as lch]
            [langohr.core               :as rmq]
            [langohr.queue              :as lq]
            [langohr.consumers          :as lc]
            [manifold.deferred          :as d]
            [manifold.stream            :as s]
            [clojure.tools.logging      :as log]))

(gen/add-encoder java.time.Instant (fn [data jsonGenerator] ( .writeString jsonGenerator (.toString data))))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defprotocol RabbitmqChannel
  (get-channel [this]))

(defn publish-handler [{:keys [pub-channel app-id service-id] :as rabbitmq} {:keys [body key id exchange]}]
  (let [payload (json/generate-string body)]
    (lb/publish pub-channel
                exchange
                key
                payload
                {:content-type "application/json"
                 :message-id id
                 :timestamp (time/to-java-date (time/instant))
                 :app-id service-id})))

(defrecord Rabbitmq [conf connection pub-stream pub-channel service-id]
  component/Lifecycle

  (start [this]
    (let [connection (rmq/connect conf)
          pub-stream (s/stream)
          pub-channel (lch/open connection)
          rabbitmq (assoc this
                          :connection connection
                          :pub-stream pub-stream
                          :pub-channel pub-channel)]
      (s/consume (partial publish-handler rabbitmq) pub-stream)
      rabbitmq
      ))

  (stop [this]
    (s/close! (:pub-stream this))
    (rmq/close (:pub-channel this))
    (rmq/close (:connection this))
    (assoc this
           :connection nil
           :pub-stream nil
           :pub-channel nil))

  RabbitmqChannel
  (get-channel [this]
    (lch/open (:connection this))))

(defn publish
  ([rabbit key body] (publish rabbit "amq.topic" key body))
  ([{:keys [pub-stream]} exchange key body]
   (let [mid (uuid)]
     (s/put! pub-stream {:key key :body body :id mid :exchange exchange})
     mid)))

(defn new-rabbitmq [env]
  (map->Rabbitmq
   {:service-id (:service-id env)
    :conf
    (apply hash-map
           (mapcat
            (fn [[k v]]
              (vector
               (keyword
                (clojure.string/join
                 "-"
                 (-> k
                     name
                     (clojure.string/split #"-")
                     rest)))
               v))
            (select-keys
             env
             (filter
              #(->> % name (re-find #"^rabbitmq-.*"))
              (keys env)))))}))

(defn convert-payload [payload content-type]
  (let [payload (String. payload)]
    (if (= "application/json" content-type)
      (json/parse-string payload true)
      payload)))

(defn rcv-msg [stream ch {:keys [delivery-tag message-id content-type reply-to correlation-id] :as meta} ^bytes payload]
  (let [body (convert-payload payload content-type)
        p (d/deferred)]
    (if reply-to
      (d/on-realized p
                     (fn [response]
                       (lb/publish ch "" reply-to (json/generate-string response) {:correlation-id correlation-id :content-type "application/json"})
                       (lb/ack ch delivery-tag))
                     (fn [_] (lb/nack ch delivery-tag false true)))
      (d/on-realized p
                     (fn [_] (lb/ack ch delivery-tag))
                     (fn [_] (lb/nack ch delivery-tag false true))))
    (->
     (s/put! stream (assoc meta :body body :response p))
     (d/chain #(if (not %) (lb/nack ch delivery-tag false true))))))

(defn queue-subscription [rabbitmq queue-name]
  (let [channel (get-channel rabbitmq)
        task-stream (s/stream)]
    (s/on-closed task-stream #(rmq/close channel))
    (lc/subscribe channel queue-name (partial rcv-msg task-stream) {:auto-ack false})
    task-stream))

(defn declare-task-queue
  ([rabbitmq queue-name] (declare-task-queue rabbitmq queue-name {}))
  ([rabbitmq queue-name options]
   (let [channel (get-channel rabbitmq)]
     (lq/declare channel queue-name (merge
                                     {:exclusive false
                                      :auto-delete false
                                      :durable true
                                      :x-dead-letter-exchange "dlq"
                                      :x-message-ttl 300000}
                                     options ))
     (lq/bind channel queue-name "amq.topic" {:routing-key queue-name})
     (rmq/close channel))))

(defn decalre-event-queue
  ([rabbitmq topic] (decalre-event-queue rabbitmq topic {}))
  ([rabbitmq topic options]
   (let [channel (get-channel rabbitmq)
         queue-name (lq/declare-server-named channel (merge {:exclusive true} options))]
     (lq/bind channel queue-name "amq.topic" {:routing-key topic})
     (rmq/close channel)
     queue-name))


  )


(comment

  (defn h [{:keys [body response]}]
    (manifold.deferred/success! response true)
    (clojure.pprint/pprint body))


 )
