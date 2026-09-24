(ns bbtl.core
  (:gen-class)
  (:require [bbtl.tasks :as tasks]
            [clojure.java.io :as io])
  (:import (java.lang ProcessBuilder ProcessBuilder$Redirect)))

(def version
  "The current bbtl release version."
  "0.1.0-SNAPSHOT")

(defn usage
  []
  (str "Usage:\n"
       "  bbtl          Pick and run a public bb task from ./bb.edn\n"
       "  bbtl version  Print the bbtl version\n"
       "\n"
       "Use bbtl from a project directory containing bb.edn.\n"))

(defn- executable-available?
  [executable]
  (try
    (zero?
     (-> (ProcessBuilder. ^java.util.List ["sh" "-c" (str "command -v " executable)])
         (.redirectOutput ProcessBuilder$Redirect/DISCARD)
         (.redirectError ProcessBuilder$Redirect/DISCARD)
         (.start)
         (.waitFor)))
    (catch Exception _
      false)))

(defn- missing-executable-message
  [executable]
  (case executable
    "bb" (str "Required executable not found: bb\n"
              "Install Babashka from https://babashka.org/ and try again.")
    (str "Required executable not found: " executable)))

(defn- preflight!
  [executables]
  (if-let [missing (some #(when-not (executable-available? %) %) executables)]
    (do
      (binding [*out* *err*]
        (println (missing-executable-message missing)))
      false)
    true))

(defn- current-bb-edn-path
  []
  (-> (io/file "bb.edn") .getAbsolutePath))

(defn- run-tasks!
  []
  (let [{:keys [status path tasks error]} (tasks/discover (current-bb-edn-path))]
    (case status
      :missing
      (do
        (binding [*out* *err*]
          (println "No bb.edn (with tasks) was found in:")
          (println (-> (io/file ".") .getCanonicalPath)))
        1)

      :invalid
      (do
        (binding [*out* *err*]
          (println "Invalid bb.edn:")
          (println path)
          (println error))
        1)

      :ok
      (if (seq tasks)
        (tasks/run-picker! tasks)
        (do
          (println "No public bb tasks found in:")
          (println path)
          0)))))

(defn run-cli!
  [& args]
  (case (vec args)
    []
    (if (preflight! ["bb"])
      (run-tasks!)
      1)

    (["-h"] ["--help"])
    (do
      (print (usage))
      0)

    (["-version"] ["--version"] ["version"])
    (do
      (println "bbtl" version)
      0)

    (do
      (binding [*out* *err*]
        (print (usage)))
      1)))

(defn -main
  [& args]
  (let [exit-code (apply run-cli! args)]
    (when-not (zero? exit-code)
      (System/exit exit-code))))
