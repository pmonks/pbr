;
; Copyright © 2021 Peter Monks
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;
; SPDX-License-Identifier: Apache-2.0
;

(ns org.pmonks.pbr
  "Peter's Build Resources.

  The following convenience fns are provided:

  exec           -- req: string or [strings]
  ensure-command -- req: command-name (string)
  git            -- opt: arguments to git (vararg strings)

  The following build task functions are provided, with the
  specified required and optional hash map options:

  deploy-info    -- opt: :deploy-info-file (defaults to \"./resources/deploy-info.edn\")
  pom            -- opt: :lib a symbol identifying your project e.g. 'org.github.pmonks/pbr
                         :version a string containing the version of your project e.g. \"1.0.0-SNAPSHOT\"
                         :pom-file the name of the file to write to (defaults to \"./pom.xml\"
                         :write-pom a flag determining whether to invoke the tools.build `write-pom` fn after generating the pom (note: it probably doesn't do what you're expecting...)
                         :pom a map containing other POM elements (see https://maven.apache.org/pom.html for details).

  All of the above build tasks return the opts hash map they were passed
  (unlike some of the functions in clojure.tools.build.api)."
  (:require [clojure.string          :as s]
            [clojure.java.io         :as io]
            [clojure.pprint          :as pp]
            [clojure.data.xml        :as xml]
            [clojure.tools.build.api :as b]
            [camel-snake-kebab.core  :as csk]
            [org.corfield.build      :as bb]))

; Lame... 🙄
(defmethod print-method java.time.Instant [^java.time.Instant inst writer]
  (print-method (java.util.Date/from inst) writer))

; ---------- CONVENIENCE FUNCTIONS ----------

(defmulti exec
  "Executes the given command line, expressed as either a string or a sequential (vector or list), optionally with other clojure.tools.build.api/process options as a second argument.

  Throws ex-info on non-zero status code."
  (fn [& args] (sequential? (first args))))

(defmethod exec true
  ([command-line] (exec command-line nil))
  ([command-line opts]
    (let [result (b/process (into {:command-args command-line} opts))]
      (if (not= 0 (:exit result))
        (throw (ex-info (str "Command '" (s/join " " command-line) "' failed (" (:exit result) ").") result))
        result))))

(defmethod exec false
  ([command-line] (exec command-line nil))
  ([command-line opts]
    (exec (s/split command-line #"\s+") opts)))

(defn ensure-command
  "Ensures that the given command is available (note: POSIX only)."
  [command]
  (try
    (exec ["command" "-v" command] {:out :capture :err :capture})
    (catch clojure.lang.ExceptionInfo _
      (throw (ex-info (str "Command " command " was not found.") {})))))

(defn git
  "Execute git with the given args, capturing and returning the output."
  [& args]
  (s/trim (:out (exec (concat ["git"] args) {:out :capture}))))


; ---------- BUILD TASK FUNCTIONS ----------

(defn deploy-info
  "Writes out a deploy-info EDN file, containing at least :hash and :date keys, and possibly also a :tag key.  opts may include a :deploy-info-file key (defaults to \"resources.deploy-info.edn\")"
  [opts]
  (let [file-name   (get opts :deploy-info-file "resources/deploy-info.edn")
        deploy-info (into {:hash (git "show" "-s" "--format=%H")
                           :date (java.time.Instant/now)}
                          (try {:tag (git "describe" "--tags" "--exact-match")} (catch clojure.lang.ExceptionInfo _ nil)))]
    (io/make-parents file-name)
    (with-open [w (io/writer (io/file file-name))]
      (pp/pprint deploy-info w)))
  opts)

(defn- pom-keyword
  "Converts a regular Clojure keyword into a POM-compatible keyword."
  [kw]
  (keyword "xmlns.http%3A%2F%2Fmaven.apache.org%2FPOM%2F4.0.0" (csk/->camelCase (name kw))))

(defn- build-pom
  "Converts the given 'element' into a POM structure in clojure.data.xml compatible EDN format."
  [elem]
  (cond
    (map-entry?  elem)             [(pom-keyword (key elem)) (build-pom (val elem))]
    (map?        elem)             (map build-pom elem)
    (sequential? elem)             (mapcat #(hash-map (pom-keyword (first elem)) (build-pom %)) (rest elem))
    (= java.util.Date (type elem)) (str (.toInstant ^java.util.Date elem))
    :else                          (str elem)))

(defn pom
  "Writes out a pom file. opts may include:

  :lib       -- opt: a symbol identifying your project e.g. 'org.github.pmonks/pbr
  :version   -- opt: a string containing the version of your project e.g. \"1.0.0-SNAPSHOT\"
  :pom-file  -- opt: the name of the file to write to (defaults to \"./pom.xml\"
  :write-pom -- opt: a flag determining whether to invoke the tools.build `write-pom` fn after generating the pom (note: it probably doesn't do what you're expecting...)
  :pom       -- opt: a map containing other POM elements (see https://maven.apache.org/pom.html for details)."
  [opts]
  (let [pom-file (get opts :pom-file "./pom.xml")
        pom-in   (merge (when (:lib     opts) {:group-id (namespace (:lib opts)) :artifact-id (name (:lib opts)) :name (name (:lib opts))})
                        (when (:version opts) {:version (:version opts)})
                        (:pom opts))   ; Merge user-supplied values last, so that they always take precedence
        pom-out  [(pom-keyword :project) {:xmlns                         "http://maven.apache.org/POM/4.0.0"
                                         (keyword "xmlns:xsi")          "http://www.w3.org/2001/XMLSchema-instance"
                                         (keyword "xsi:schemaLocation") "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"}
                   (concat [[(pom-keyword :model-version) "4.0.0"]]
                            (build-pom pom-in))]
        pom-xml  (xml/sexp-as-element pom-out)]
    (with-open [pom-writer (io/writer pom-file)]
      (xml/emit pom-xml pom-writer :encoding "UTF8"))
    (when (:write-pom opts)
      (b/write-pom (merge (assoc opts :src-pom pom-file)
                          (when-not (:basis     opts) {:basis     (bb/default-basis)})
                          (when-not (:class-dir opts) {:class-dir (bb/default-class-dir)})))))
  opts)
