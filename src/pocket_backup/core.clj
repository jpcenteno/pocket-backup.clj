(ns pocket-backup.core
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [org.httpkit.client :as client]))

; ╔════════════════════════════════════════════════════════════════════════╗
; ║ Client secrets                                                         ║
; ╚════════════════════════════════════════════════════════════════════════╝

(defn parse-secrets
  "Parses the secrets.env contents from string s.
  Returns {:consumer-key ... :access-token ...} or throws if missing."
  [s]
  (let [lines (clojure.string/split-lines s)
        kvs   (into {} (keep (fn [line]
                               (when-let [[_ k v] (re-matches #"(?i)^\s*([^#=\s]+)\s*=\s*(.*)\s*$" line)]
                                 [(clojure.string/upper-case k) (clojure.string/trim v)]))
                             lines))
        consumer-key (get kvs "POCKET_CONSUMER_KEY")
        access-token (get kvs "POCKET_ACCESS_TOKEN")]
    (when (or (clojure.string/blank? consumer-key)
              (clojure.string/blank? access-token))
      (throw (ex-info "Missing or empty CONSUMER_KEY or ACCESS_TOKEN in secrets.env"
                      {:found kvs})))
    {:consumer-key consumer-key
     :access-token access-token}))

; ╔════════════════════════════════════════════════════════════════════════╗
; ║ Logging                                                                ║
; ╚════════════════════════════════════════════════════════════════════════╝

(def *logs
  (atom []))

(defn log!
  [entry]
  (swap! *logs conj entry)
  (binding [*out* *err*]
    (println (str "[LOG] " entry))))

; ╔════════════════════════════════════════════════════════════════════════╗
; ║ API Wrapper                                                            ║
; ╚════════════════════════════════════════════════════════════════════════╝

(defn request
  [secrets offset]
  (let [url     "https://getpocket.com/v3/get"
        headers {"Content-Type" "application/json; charset=UTF8"}
        body    {"consumer_key" (:consumer-key secrets)
                 "access_token" (:access-token secrets)
                 "detailType"   "complete"
                 "count"        30
                 "offset"       offset
                 "total"        1}
        response @(client/request {:url url
                                   :method :post
                                   :headers headers
                                   :body (json/write-str body)})
        status   (:status response)
        parsed   (try
                   (json/read-str (:body response))
                   (catch Exception _
                     (throw (ex-info "Failed to parse JSON body" {:body (:body response)}))))]
    (when (not= status 200)
      (throw (ex-info "Non-200 HTTP status" {:status status :body parsed})))
    (when-not (contains? parsed "list")
      (throw (ex-info "Missing `list` key in response body" {:parsed parsed})))
    (vals (get parsed "list"))))

; ╔════════════════════════════════════════════════════════════════════════╗
; ║ Backup function                                                        ║
; ╚════════════════════════════════════════════════════════════════════════╝

(defn backup [output-file secrets offset]
  (with-open [w (io/writer output-file :append true)]
    (loop [offset offset]
    ; Request items:
      (log! {:event :requesting :offset offset})
      (let [items (request secrets offset)
            n     (count items)]
        (log! {:event :response :offset offset :count n})

      ; Write response items:
        (doseq [item items
                :let
                [line (json/write-str item)]]
          (.write w line)
          (.write w "\n"))
        (.flush w)
        (log! {:event :wrote :offset offset :count n :file output-file})

        (if (zero? n)
          (log! {:event :done :offset offset})
          (do (Thread/sleep 500)
              (recur (+ offset n))))))))

; ╔════════════════════════════════════════════════════════════════════════╗
; ║ Manual usage helpers                                                   ║
; ╚════════════════════════════════════════════════════════════════════════╝

(comment
  ; Read your app/user secrets:
  (def secrets (parse-secrets (slurp "./secrets.env")))

  ; Play with the request function
  (def response (request secrets 0))
  (-> response count)

  ; Do a backup. Update the offset if something goes wrong:
  (backup "backup.jsonl" secrets 0)

  nil)
