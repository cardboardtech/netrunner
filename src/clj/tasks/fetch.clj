(ns tasks.fetch
  "NetrunnerDB import tasks"
  (:require [web.db :refer [db] :as webdb]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [tasks.nrdb :refer :all]
            [tasks.altart :refer [add-art]]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]))

(defn fetch-with-db
  "Import data from NRDB. Assumes the database is already open. See `fetch` for arguments."
  [& args]
  (let [localpath (first (remove #(string/starts-with? % "--") args))
        download-images (not (some #{"--no-card-images"} args))
        data (fetch-data localpath download-images)]
    (println (count (:cycles data)) "cycles imported")
    (println (count (:sets data)) "sets imported")
    (println (count (:mwls data)) "MWL versions imported")
    (println (count (:cards data)) "cards imported")
    (add-art false)
    (update-config)))

(defn fetch
  "Import data from NetrunnerDB.
  Can accept `--local <path>` to use the `netrunner-card-json` project locally,
  otherwise pulls data from NRDB.
  Specifying `--no-card-images` will not attempt to download images for cards."
  [& args]

  (webdb/connect)
  (try
    (apply fetch-with-db args)
    (catch Exception e (do
                         (println "Import data failed:" (.getMessage e))
                         (.printStackTrace e)))
    (finally (webdb/disconnect))))

(defn usage
  [options-summary]
  (->> ["Usage: lein fetch [options] target"
        ""
        "Targets:"
        "  edn     Fetch edn data (card definitions, format info, etc.) from GitHub or a local path"
        "  images  Fetch card images from NetrunnerDB"
        "  all     Fetch edn data and images (default)"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(def cli-options
  [["-l" "--local PATH" "Path to fetch card edn from"
    :validate [#(-> %
                    (str "/edn/raw_data.edn")
                    io/file
                    .exists)
               "Could not find local data file"]]
   ["-d" "--db" "Load card data into the database"
    :id :db
    :default true]
   ["-n" "--no-db" "Do not load card data into the database"
    :id :db
    :parse-fn not]])

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      errors {:exit-message (string/join \newline errors)})))

(defn exit [status msg]
  (binding [*out* *err*]
    (println msg))
  (System/exit status))

(defn command
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (if errors
      (exit 1 (string/join \newline (conj errors "" (usage summary))))
      (case (first arguments)
        "edn" (fetch-edn options)
        "images" (fetch-images options)
        (do
          (fetch-edn options)
          (fetch-images options))))))
