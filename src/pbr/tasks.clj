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

(ns pbr.tasks
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
                         :pom-file the name of the file to write to (defaults to \"./pom.xml\")
                         :write-pom a flag determining whether to invoke \"clj -Spom\" after generating the basic pom (i.e. adding dependencies and repositories from your deps.edn file)
                         :pom a map containing other POM elements (see https://maven.apache.org/pom.html for details).
  licenses       -- opt: :output output format, one of :summary, :detailed (defaults to :summary)
                         :verbose boolean controlling whether to emit verbose output or not (defaults to false)
  check-release  -- as for the release task --
  release        -- req: :lib a symbol identifying your project e.g. 'org.github.pmonks/pbr
                         :version a string containing the version of your project e.g. \"1.0.0-SNAPSHOT\"
                    opt: :dev-branch the name of the development branch containing the changes to be PRed (defaults to \"dev\")
                         :prod-branch the name of the production branch where the PR is to be sent (defaults to \"main\")
                         :pr-desc a format string used for the PR description with two %s values passed in (%1$s = lib, %2$s = version) (defaults to \"%1$s release v%2$s. See commit log for details of what's included in this release.\")
                         -- all opts from the deploy-info task --

  All of the above build tasks return the opts hash map they were passed
  (unlike some of the functions in clojure.tools.build.api)."
  (:require [clojure.string           :as s]
            [clojure.java.io          :as io]
            [clojure.pprint           :as pp]
            [clojure.data.xml         :as xml]
            [clojure.tools.deps.alpha :as d]
            [org.corfield.build       :as bb]
            [camel-snake-kebab.core   :as csk]
            [pbr.convenience          :as pbrc :refer [ensure-command exec git]]
            [pbr.licenses             :as lic]))

; Since v1.10 this should be in core...
(defmethod print-method java.time.Instant [^java.time.Instant inst writer]
  (print-method (java.util.Date/from inst) writer))

(defn deploy-info
  "Writes out a deploy-info EDN file, containing at least :hash and :date keys, and possibly also a :tag key.  opts includes:

  :deploy-info-file -- req: the name of the file to write to (e.g. \"./resources/deploy-info.edn\")"
  [opts]
  (ensure-command "git")
  (if-let [file-name (:deploy-info-file opts)]
    (let [deploy-info (into {:hash (git "show" "-s" "--format=%H")
                             :date (java.time.Instant/now)}
                            (try {:tag (git "describe" "--tags" "--exact-match")} (catch clojure.lang.ExceptionInfo _ nil)))]
      (io/make-parents file-name)
      (with-open [w (io/writer (io/file file-name))]
        (pp/pprint deploy-info w)))
    (throw (ex-info ":deploy-info-file not provided" (into {} opts))))
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
  "Writes out a pom file. opts includes:

  :lib       -- opt: a symbol identifying your project e.g. 'org.github.pmonks/pbr
  :version   -- opt: a string containing the version of your project e.g. \"1.0.0-SNAPSHOT\"
  :pom-file  -- opt: the name of the file to write to (defaults to \"./pom.xml\")
  :write-pom -- opt: a flag determining whether to invoke \"clj -Spom\" after generating the basic pom (i.e. adding dependencies and repositories from your deps.edn file)
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
      (exec "clojure -Srepro -Spom")))   ; tools.build/write-pom is nowhere as useful as clojure -Spom but the latter doesn't have an API so we just exec it instead...
  opts)

(defn licenses
  "Lists all licenses used transitively by the project.

  :output  -- opt: output format, one of :summary, :detailed, :edn (defaults to :summary)
  :verbose -- opt: boolean controlling whether to emit verbose output or not (defaults to false)"
  [opts]
  (let [basis        (bb/default-basis)
        lib-map      (d/resolve-deps basis {})
        _            (d/prep-libs! lib-map {:action :prep :log :info} {})  ; Make sure everything is "prepped" (downloaded locally) before we start looking for licenses
        verbose      (get opts :verbose false)
        dep-licenses (into {} (for [[k v] lib-map] (lic/dep-licenses verbose k v)))]
    (case (get opts :output :summary)
      :summary  (let [freqs    (frequencies (filter identity (mapcat :licenses (vals dep-licenses))))
                      licenses (seq (sort (keys freqs)))]
                  (println "Licenses in upstream dependencies (occurrences):")
                  (if licenses
                    (doall (map #(println "  *" % (str "(" (get freqs %) ")")) licenses))
                    (println "  <no licenses found>")))
      :detailed (let [direct-deps     (into {} (remove (fn [[_ v]] (seq (:dependents v))) dep-licenses))
                      transitive-deps (into {} (filter (fn [[_ v]] (seq (:dependents v))) dep-licenses))]
                  (println "Direct dependencies:")
                  (if direct-deps
                    (doall (for [[k v] (sort-by key direct-deps)] (println "  *" (str k ":") (s/join ", " (:licenses v)))))
                    (println "  - none -"))
                  (println "\nTransitive dependencies:")
                  (if transitive-deps
                    (doall (for [[k v] (sort-by key transitive-deps)] (println "  *" (str k ":") (s/join ", " (:licenses v)))))
                    (println "  - none -")))
      :edn      (pp/pprint dep-licenses))
    (let [deps-without-licenses (seq (sort (keys (remove #(:licenses (val %)) dep-licenses))))]
      (when deps-without-licenses
        (println "These dependencies do not appear to include licensing information in their published artifacts:")
        (doall (map (partial println "  *") deps-without-licenses))
        (println "Please raise a bug at https://github.com/pmonks/pbr/issues/new?assignees=&labels=&template=Bug_report.md and include this message.")))
    opts))

(defn check-release
  "Check that a release can be made from the current directory, with the given opts."
  [opts]
  ; Check for the command line tools we need
  (ensure-command "git")
  (ensure-command "hub")

  ; Check that opts map is properly populated
  (when-not (:version opts) (throw (ex-info ":version not provided" (into {} opts))))
  (when-not (:lib opts)     (throw (ex-info ":lib not provided" (into {} opts))))

  ; Check status of working directory
  (let [dev-branch     (get opts :dev-branch "dev")
        current-branch (s/trim (:out (exec "git branch --show-current" {:out :capture})))]
    (when-not (= dev-branch current-branch)
      (throw (ex-info (str "Must be on branch '" dev-branch "' to prepare a release, but current branch is '" current-branch "'.") {}))))

  (let [git-status (exec "git status --short" {:out :capture})]
    (when (or (not (s/blank? (:out git-status)))
              (not (s/blank? (:err git-status))))
      (throw (ex-info (str "Working directory is not clean:\n" (:out git-status) "Please commit, revert, or stash these changes before preparing a release.") git-status))))

  opts)

(defn release
  "Release a new version of the code via a PR to main. opts includes:

  :lib         -- req: a symbol identifying your project e.g. 'org.github.pmonks/pbr
  :version     -- req: a string containing the version of your project e.g. \"1.0.0-SNAPSHOT\"
  :dev-branch  -- opt: the name of the development branch containing the changes to be PRed (defaults to \"dev\")
  :prod-branch -- opt: the name of the production branch where the PR is to be sent (defaults to \"main\")
  :pr-desc     -- opt: a format string used for the PR description with two %s values passed in (%1$s = lib, %2$s = version) (defaults to \"%1$s release v%2$s. See commit log for details of what's included in this release.\")
  -- opts from the (deploy-info) task, if you wish to generate deploy-info --"
  [opts]
  (when-not (:version opts) (throw (ex-info ":version not provided" (into {} opts))))
  (when-not (:lib opts)     (throw (ex-info ":lib not provided" (into {} opts))))

  (let [lib              (:lib opts)
        version          (:version opts)
        tag-name         (str "v" version)
        dev-branch       (get opts :dev-branch "dev")
        prod-branch      (get opts :prod-branch "main")
        deploy-info-file (:deploy-info-file opts)]

    (println (str "ℹ️ Preparing to release " lib " " tag-name "..."))

    ; Ensure working directory is up to date with prod branch
    (println "ℹ️ Updating working directory...")
    (git "fetch" "origin" (str prod-branch ":" prod-branch))
    (git "merge" prod-branch)
    (git "pull")

    (println "ℹ️ Checking that a release can be made...")
    (check-release opts)

    (println (str "ℹ️ All good; press any key to continue or Ctrl+C to abort..."))
    (flush)
    (read-line)

    (println "ℹ️ Tagging release as" (str tag-name "..."))
    (git "tag" "-f" "-a" tag-name "-m" (str "Release " tag-name))

    (when deploy-info-file
      (println "ℹ️ Updating" (str deploy-info-file "..."))
      (deploy-info opts)
      (git "add" deploy-info-file)  ; Add the file just in case it's never existed before - this is no-op if it's already in the index
      (git "commit" "-m" (str ":gem: Release " tag-name) deploy-info-file))

    (println "ℹ️ Pushing" deploy-info-file "and tag" (str tag-name "..."))
    (git "push")
    (git "push" "origin" "-f" "--tags")

    (println "ℹ️ Creating pull request...")
    (let [pr-desc-fmt (get opts :pr-desc "%1$s release v%2$s. See commit log for details of what's included in this release.")]
      (exec ["hub" "pull-request" "--browse" "-f"
             "-m" (str "Release v" version)
             "-m" (format pr-desc-fmt (str lib) (str version))
             "-h" dev-branch "-b" prod-branch]))

    (println "ℹ️ After the PR has been merged, it is highly recommended to:\n"
             "  1. git fetch origin" (str prod-branch ":" prod-branch) "\n"
             "  2. git merge" prod-branch "\n"
             "  3. git pull\n"
             "  4. git push")

    (println "⏹ Done."))
  opts)
