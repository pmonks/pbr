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

(ns pbr.licenses
  (:require [clojure.string   :as s]
            [clojure.java.io  :as io]
            [clojure.edn      :as edn]
            [clojure.data.xml :as xml]
            [xml-in.core      :as xi]
            [pbr.spdx         :as spdx]))

(def ^:private fallbacks (edn/read (java.io.PushbackReader. (io/reader (io/resource "pbr/fallbacks.edn")))))

(defmulti ^:private filename
  "Returns just the name component of the given file or path string."
  type)

(defmethod filename java.io.File
  [^java.io.File f]
  (.getName f))

(defmethod filename java.lang.String
  [s]
  (last (s/split s #"/")))   ; TODO: consider using (re-pattern (str java.io.File/separatorChar)) here, though ZIP files always use "/" regardless of platform...

(defmethod filename java.util.zip.ZipEntry
  [^java.util.zip.ZipEntry ze]
  (filename (.getName ze)))   ; Zip Entry names include the entire path

(defn- lookup-license-name
  [verbose dep name]
  (if-let [spdx-expr (spdx/guess name)]
    spdx-expr
    (when verbose (println "⚠️ The license text" (str "'" name "',") "found in dep" (str "'" dep "',")  "has no SPDX equivalent."))))

(defn- lookup-license-url
  [verbose dep url]
  (if-let [spdx-expr (spdx/license-url->spdx-id url)]
    spdx-expr
    (when verbose (println "⚠️ The license url" (str "'" url "',") "found in dep" (str "'" dep "',")  "does not map to a SPDX identifier."))))

(defmulti licenses-from-file
  "Attempts to determine the license(s) for the given file."
  (fn [_ _ name _] (s/lower-case (filename name))))

(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")

(defmethod licenses-from-file "pom.xml"
  [verbose dep _ input-stream]
  (let [pom-xml (xml/parse input-stream)]
    (if-let [pom-licenses (seq
                            (distinct
                              (filter identity
                                      (concat (map (partial lookup-license-name verbose dep) (xi/find-all pom-xml [::pom/project ::pom/licenses ::pom/license ::pom/name]))
                                              (map (partial lookup-license-url  verbose dep) (xi/find-all pom-xml [::pom/project ::pom/licenses :pom/:license ::pom/url]))
                                              ; Note: a few rare pom.xml files are missing an xmlns declation (e.g. software.amazon.ion/ion-java) - the following two lines will catch those
                                              (map (partial lookup-license-name verbose dep) (xi/find-all pom-xml [:project      :licenses      :license      :name]))
                                              (map (partial lookup-license-url  verbose dep) (xi/find-all pom-xml [:project      :licenses      :license      :url]))))))]
      pom-licenses
      (when verbose (println "ℹ️" dep "has a pom.xml file but it does not contain a <licenses> element")))))

(defmethod licenses-from-file "license.spdx"
  [_ _ name _]
  (println "⚠️ Processing" (str "'" name "'") "files is not yet implemented.")
  (flush)
  nil)

; Note: ideally this should use the mechanism described at https://spdx.dev/license-list/matching-guidelines/
(defmethod licenses-from-file :default
  [verbose dep _ input-stream]
  (let [rdr         (io/reader input-stream)    ; Note: we don't wrap this in "with-open", since the input-stream we're handed is closed by the calling fns
        first-lines (s/trim (s/join " " (take 2 (remove s/blank? (map s/trim (line-seq rdr))))))]  ; Take the first two non-blank lines, since many licenses put the name on line 1, and the version on line 2
    [(lookup-license-name verbose dep first-lines)]))

(def ^:private probable-license-filenames #{"license.spdx" "pom.xml" "license" "license.txt" "copying"})   ;####TODO: consider "license.md" and #".+\.spdx" (see https://github.com/spdx/spdx-maven-plugin for why the latter is important)...

(defn- probable-license-file?
  "Returns true if the given file is a probable license file, false otherwise."
  [f]
  (contains? probable-license-filenames (s/lower-case (filename f))))

(defn- jar-licenses
  "Attempts to determine the license(s) used by the given JAR file."
  [dep verbose jar-file]
  (with-open [zip (java.util.zip.ZipInputStream. (io/input-stream jar-file))]
    (loop [licenses      []
           license-files []
           entry         (.getNextEntry zip)]
      (if entry
        (if (probable-license-file? entry)
          (recur (doall (concat licenses (licenses-from-file verbose dep (filename entry) zip))) (concat license-files [(.getName entry)]) (.getNextEntry zip))
          (recur licenses license-files (.getNextEntry zip)))
        (do
          (when verbose (println "ℹ️" dep (str "(" jar-file ")") "contains" (count license-files) "probable license file(s):" (s/join ", " license-files)))
          licenses)))))

(defmulti dep-licenses
  "Attempts to determine the license(s) used by the given dep."
  (fn [_ _ info] (:deps/manifest info)))

; :mvn dependencies are one or more JARs on disk
(defmethod dep-licenses :mvn
  [verbose dep info]
  (let [jar-files (:paths info)
        licenses  (if-let [licenses (seq (distinct (filter #(not (s/blank? %)) (mapcat (partial jar-licenses dep verbose) jar-files))))]
                    licenses
                    (get-in fallbacks [dep :licenses]))]
    (when verbose (println "ℹ️" dep "contains" (count licenses) "license(s):" (s/join ", " licenses)))
    [dep (merge info (when licenses {:licenses licenses}))]))

; :deps dependencies are simple uncompressed directory structures on disk
(defmethod dep-licenses :deps
  [verbose dep info]
  (let [license-files (seq (filter probable-license-file? (file-seq (io/file (:deps/root info)))))
        _             (when verbose (println "ℹ️" dep "contains" (count license-files) "probable license file(s):" (s/join ", " license-files)))
        licenses      (if-let [licenses (seq (distinct (filter #(not (s/blank? %))
                                                               (mapcat #(with-open [is (io/input-stream %)] (licenses-from-file verbose dep (filename %) is))
                                                                       license-files))))]
                        licenses
                        (get-in fallbacks [dep :licenses]))]
    (when verbose (println "ℹ️" dep "contains" (count licenses) "license(s):" (s/join ", " licenses)))
    [dep (merge info (when licenses {:licenses licenses}))]))

(defmethod dep-licenses :default
  [_ dep info]
  (throw (ex-info (str "Unexpected manifest type" (:deps/manifest info) "for dependency" dep) {dep info})))
