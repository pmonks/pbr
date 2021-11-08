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
  "Standard implementations of common tools.build tasks.

  The following build task functions are provided, with the
  specified required and optional hash map options:

  deploy-info    -- opt: :deploy-info-file (defaults to \"./resources/deploy-info.edn\")
  check-release  -- as for the release task --
  release        -- req: :lib a symbol identifying your project e.g. 'org.github.pmonks/pbr
                         :version a string containing the version of your project e.g. \"1.0.0-SNAPSHOT\"
                    opt: :dev-branch the name of the development branch containing the changes to be PRed (defaults to \"dev\")
                         :prod-branch the name of the production branch where the PR is to be sent (defaults to \"main\")
                         :pr-desc a format string used for the PR description with two %s values passed in (%1$s = lib, %2$s = version) (defaults to \"%1$s release v%2$s. See commit log for details of what's included in this release.\")
                         -- all opts from the deploy-info task --

  All of the above build tasks return the opts hash map they were passed
  (unlike some of the functions in clojure.tools.build.api)."
  (:require [clojure.string        :as s]
            [clojure.java.io       :as io]
            [clojure.pprint        :as pp]
            [org.corfield.build    :as bb]
            [tools-convenience.api :as tc :refer [exec git]]
            [tools-pom.tasks       :as pom]))

(defn deploy-info
  "Writes out a deploy-info EDN file, containing at least :hash and :date keys, and possibly also a :tag key.  opts includes:

  :deploy-info-file -- req: the name of the file to write to (e.g. \"./resources/deploy-info.edn\")"
  [opts]
  (if-let [file-name (:deploy-info-file opts)]
    (let [deploy-info (merge {:hash (tc/git-current-commit)
                              :date (java.time.Instant/now)}
                              (when-let [tag (try (tc/git-nearest-tag) (catch clojure.lang.ExceptionInfo _ nil))] {:tag tag}))]
      (io/make-parents file-name)
      (with-open [w (io/writer (io/file file-name))]
        (pp/pprint deploy-info w)))
    (throw (ex-info ":deploy-info-file not provided" (into {} opts))))
  opts)

(defn check-release
  "Check that a release can be made from the current directory, with the given opts."
  [opts]
  ; Check for the command line tools we need
  (tc/ensure-command "hub")

  ; Check that opts map is properly populated
  (when-not (:version opts) (throw (ex-info ":version not provided" (into {} opts))))
  (when-not (:lib opts)     (throw (ex-info ":lib not provided" (into {} opts))))

  ; Check status of working directory
  (let [dev-branch     (get opts :dev-branch "dev")
        current-branch (tc/git-current-branch)]
    (when-not (= dev-branch current-branch)
      (throw (ex-info (str "Must be on branch '" dev-branch "' to prepare a release, but current branch is '" current-branch "'.") {}))))

  (let [git-status (git "status" "--short")]
    (when (not (s/blank? git-status))
      (throw (ex-info (str "Working directory is not clean:\n " git-status "\nPlease commit, revert, or stash these changes before preparing a release.") {}))))

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
        dev-branch       (get opts :dev-branch "dev")
        prod-branch      (get opts :prod-branch "main")
        deploy-info-file (:deploy-info-file opts)]

    (println (str "ℹ️ Preparing to release " lib " " version "..."))

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

    (println "ℹ️ Tagging release as" (str version "..."))
    (git "tag" "-f" "-a" version "-m" (str "Release " version))

    (when deploy-info-file
      (println "ℹ️ Updating" (str deploy-info-file "..."))
      (deploy-info opts)
      (git "add" deploy-info-file)  ; Add the file just in case it's never existed before - this is no-op if it's already in the index
      (git "commit" "-m" (str ":gem: Release " version) deploy-info-file))

    (println "ℹ️ Pushing tag" version (str "(" (tc/git-tag-commit version) ")..."))
    (git "push")
    (git "push" "origin" "-f" "--tags")

    (println "ℹ️ Creating 'release' pull request from" dev-branch " to " prod-branch "...")
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

(defn deploy
  "Builds and deploys the library's artifacts (pom.xml, JAR) to Clojars (or elsewhere), from the 'production' branch. opts includes:

  :prod-branch -- opt: the name of the production branch where the deployment is to be initiated from (defaults to \"main\")
  -- opts from the pom task, though note that :write-pom and :validate-pom are forced to true --
  -- opts from the build-clj/jar task --
  -- opts from the build-clj/deploy task (i.e. deps-deploy) --"
  [opts]
  (let [current-branch (tc/git-current-branch)
        main-branch    (get opts :prod-branch "main")]
    (if (= current-branch main-branch)
      (let [deploy-opts (assoc opts :version      (tc/git-nearest-tag)
                                    :write-pom    true
                                    :validate-pom true)]
        (println "ℹ️ Deploying" (:lib deploy-opts) "version" (:version deploy-opts) "to Clojars.")
        (pom/pom   deploy-opts)
        (bb/jar    deploy-opts)
        (bb/deploy deploy-opts))
      (throw (ex-info (str "deploy task must be run from '" main-branch "' branch (current branch is '" current-branch "').") (into {} opts)))))
  opts)
