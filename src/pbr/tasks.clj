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
  "Standard implementations of common tools.build tasks."
  (:require [clojure.string        :as s]
            [clojure.java.io       :as io]
            [clojure.pprint        :as pp]
            [clojure.java.shell    :as sh]
            [clojure.data.json     :as json]
            [antq.core             :as aq]
            [antq.upgrade          :as au]
            [clj-kondo.core        :as kd]
;            [eastwood.lint         :as ew]
            [codox.main            :as cx]
;            [nvd.task              :as nvd]
            [org.corfield.build    :as bb]
            [tools-convenience.api :as tc]
            [tools-pom.tasks       :as pom]))

(def ^:private ver-clj-check   {:git/sha "518d5a1cbfcd7c952f548e6dbfcb9a4a5faf9062"}) ; Latest version of https://github.com/athos/clj-check
(def ^:private ver-test-runner {:git/tag "v0.5.1" :git/sha "dfb30dd"})                ; Latest version of https://github.com/cognitect-labs/test-runner
(def ^:private ver-logback     {:mvn/version "1.2.11"})
(def ^:private ver-slf4j       {:mvn/version "1.7.36"})
(def ^:private ver-eastwood    {:mvn/version "1.2.4"})

(defn github-url
  "Returns the base GitHub URL for the given lib (a namespaced symbol), or nil if it can't be determined. Note: this is a utility fn, not a task fn."
  [lib]
  (when lib
    (let [ns (namespace lib)
          nm (name lib)]
      (when (and (not (s/blank? ns))
                 (s/starts-with? ns "com.github.")
                 (not (s/blank? nm)))
        (str "https://github.com/" (s/replace ns "com.github." "") "/" nm)))))

(defn check
  "Check the code by compiling it (and throwing away the result). No options."
  [opts]
  ; Note: we do this this way to get around tools.deps lack of support for transitive dependencies that are git coords
  (tc/clojure "-Sdeps"
              (str "{:aliases {:check {:extra-deps {com.github.athos/clj-check " (pr-str ver-clj-check) "} :main-opts [\"-m\" \"clj-check.check\"]}}}")
              "-M:check")
  opts)

(defn- outdated-deps
  "Utility fn to determine outdated dependencies using antq."
  [opts]
  (let [user-opts (:antq opts)
        antq-opts (merge {:directory     ["."]
                          :reporter      "table"}
                         user-opts
                         {:ignore-locals true                                                ; Always ignore local-only deps
                          :skip          (concat ["pom" "leiningen"] (:skip user-opts))})    ; Always skip pom.xml and project.clj files
        deps      (aq/fetch-deps antq-opts)]
    (aq/antq antq-opts deps)))    ; NOTE: DO NOT RETURN opts HERE!  THIS IS NOT A BUILD TASK FN!

(defn antq-outdated
  "Determine outdated dependencies, via antq. opts includes:

  :antq -- opt: a map containing antq-specific configuration options. Sadly these aren't really documented anywhere obvious, but they are passed into this fn: https://github.com/liquidz/antq/blob/main/src/antq/core.clj#L230"
  [opts]
  (let [old-deps (outdated-deps opts)]
    (when (seq old-deps)
      (throw (ex-info "Outdated dependencies found" {:outdated-deps old-deps}))))
  opts)

(defn antq-upgrade
  "Unconditionally upgrade any outdated dependencies, via antq. opts includes:

  :antq -- opt: a map containing antq-specific configuration options. Sadly these aren't really documented anywhere obvious, but they are passed into this fn: https://github.com/liquidz/antq/blob/main/src/antq/core.clj#L230"
  [opts]
  (let [old-deps (outdated-deps opts)]
    (when (seq old-deps)
      (au/upgrade! old-deps (assoc (:antq opts) :force true :ignore-locals true))))
  opts)

(defn run-tests
  "Runs unit tests (if any). opts includes:

  :test-paths -- opt: a sequence of paths containing test code (defaults to [\"test\"])
  :test-deps  -- opt: a dep map of dependencies to add while testing"
  [opts]
  (let [test-paths (vec (get opts :test-paths ["test"]))
        test-deps  (merge {'io.github.cognitect-labs/test-runner ver-test-runner
                           'ch.qos.logback/logback-classic       ver-logback
                           'org.slf4j/slf4j-api                  ver-slf4j
                           'org.slf4j/jcl-over-slf4j             ver-slf4j
                           'org.slf4j/log4j-over-slf4j           ver-slf4j
                           'org.slf4j/jul-to-slf4j               ver-slf4j}
                          (:test-deps opts))]
    ; Note: we do this this way to get around tools.deps lack of support for transitive dependencies that are git coords
    (tc/clojure "-Sdeps"
                (str "{:aliases {:test {:extra-paths " (pr-str test-paths) " "
                                       ":extra-deps  " (pr-str test-deps) " "
                                       ":main-opts   [\"-m\" \"cognitect.test-runner\"] "
                                       ":exec-fn     cognitect.test-runner.api/test}}}")
                "-X:test"))
  opts)

(defn uber
  "Create an uber jar. opts includes:

  :uber-file -- opt: the name of the uberjar file to emit (defaults to the logic in the `build-clj/default-jar-file` fn).
  -- opts from the `pom` task --"
  [opts]
  (pom/pom opts)
  (bb/uber opts)
  opts)

(defn uberexec
  "Creates an executable uber jar (note: does not bundle a JRE, though one is still required). opts includes:

  :uber-file -- opt: the name of the uberjar file to emit (defaults to the logic in the `build-clj/default-jar-file` fn). The executable jar will have the same name, but without the '.jar' extension.
  -- opts from the `pom` task --"
  [opts]
  (let [uber-file     (or (:uber-file opts) (bb/default-jar-file (:target opts) (:lib opts) (:version opts)))
        uberexec-file (s/replace uber-file ".jar" "")]
    (uber opts)
    (println "Building executable uberjar" (str uberexec-file "..."))
    ; Magical cross-platform script that gets prepended to the JAR file
    (spit uberexec-file (str ":; java -jar $0 \"$@\" #\r\n"
                             ":; exit $? #\r\n"
                             "\r\n"
                             "@ECHO OFF\r\n"
                             "SET ARGS=%1\r\n"
                             "SHIFT\r\n"
                             ":nextarg\r\n"
                             "IF [%1] == [] GOTO done\r\n"
                             "SET ARGS=%ARGS% %1\r\n"
                             "SHIFT\r\n"
                             "GOTO nextarg\r\n"
                             ":done\r\n"
                             "java -jar %~f0 %ARGS%\r\n"
                             "EXIT /b %ERRORLEVEL%\r\n"))
    (with-open [in (io/input-stream uber-file)]
      (with-open [out (io/output-stream uberexec-file :append true)]
        (io/copy in out)))
    (.setExecutable (io/file uberexec-file) true false))
  opts)


(defn- delete-dir
  "Deletes the given directory and all of its contents, recursively"
  [dir]
  (let [d (io/file dir)]
    (when (.exists d)
      (doall (map io/delete-file (reverse (file-seq d)))))))

(defn nvd
  "Run the NVD vulnerability checker

  :nvd -- opt: a map containing nvd-clojure-specific configuration options. See https://github.com/rm-hull/nvd-clojure#configuration-options"
  [opts]
  ; Notes: NVD *cannot* be run in a directory containing a deps.edn, as this "pollutes" the classpath of the JVM it's running in; something it is exceptionally sensitive to.
  ; So we create a temporary directory underneath the current project, and run it there. Yes this is ridiculous.
  (let [nvd-opts           (merge {:fail-threshold 11                 ; By default tell NVD not to fail under any circumstances
                                   :output-dir     "../target/nvd"}   ; Write to the project's actual target directory
                                  (:nvd opts))
        classpath-to-check (s/replace
                             (s/replace (s/trim (:out (tc/clojure-silent "-Spath" "-A:any:aliases")))
                                        #":[^:]*/org/owasp/dependency-check-core/[\d\.]+/dependency-check-core-[\d\.]+.jar:"   ; Remove dependency-check jar, if present
                                        ":")
                             #":[^:]*/nvd-clojure/nvd-clojure/[\d\.]+/nvd-clojure-[\d\.]+\.jar:"                               ; Remove nvd-clojure jar, if present
                             ":")]
    (delete-dir      "target/nvd")
    (delete-dir      ".nvd")
    (io/make-parents ".nvd/.")
    (spit ".nvd/nvd-options.json"
          (json/write-str {:delete-config? false
                           :nvd            nvd-opts}))
    (let [nvd-result (sh/sh "clojure"
                            "-J-Dclojure.main.report=stderr"
                            "-Srepro"
                            "-Sdeps"
                            "{:deps {nvd-clojure/nvd-clojure {:mvn/version \"RELEASE\"}}}"
                            "-M"
                            "-m"
                            "nvd.task.check"
                            "nvd-options.json"   ; Note: relative to :dir
                            classpath-to-check
                            :dir ".nvd")]
      ; Note: we don't print stderr, as that's where dependency-check's (voluminous) logs go
      (when-not (s/blank? (:out nvd-result))
        (println (:out nvd-result)))
      (when-not (= 0 (:exit nvd-result))
        (throw (ex-info "NVD failed" nvd-result))))

    (delete-dir ".nvd"))
  opts)

(defn kondo
  "Run the clj-kondo linter. No options."
  [opts]
  (let [basis (bb/default-basis)
        paths (get basis :paths ["src"])]
    (kd/print! (kd/run! {:lint paths})))
  opts)

(defn eastwood
  "Run the eastwood linter. opts includes:

  :eastwood -- opt: a map containing eastwood-specific configuration options (see https://github.com/jonase/eastwood#running-eastwood-in-a-repl)"
  [opts]
  (let [basis         (bb/default-basis)
        paths         (get basis :paths ["src"])
        eastwood-opts (merge {:source-paths paths}
                             (:eastwood opts))]
    ; Note: we can't do this, as it's running in the wrong classpath (i.e. the build.tool classpath, not the project classpath)
    ;(ew/eastwood eastwood-opts))
    ; So instead we revert to ye olde dynamic invocation...
    (tc/clojure "-Sdeps"
                (str "{:aliases {:eastwood {:extra-deps {jonase/eastwood " (pr-str ver-eastwood) "} "
                                           ":main-opts  [\"-m\" \"eastwood.lint\" " (pr-str eastwood-opts) "]}}}")
                "-M:eastwood"))
  opts)

(defn deploy-info
  "Writes out a deploy-info EDN file, containing at least :hash and :date keys, and possibly also :repo and :tag keys. opts includes:

  :deploy-info-file -- req: the name of the file to write to (e.g. \"./resources/deploy-info.edn\")"
  [opts]
  (if-let [file-name (:deploy-info-file opts)]
    (let [deploy-info (merge {:hash (tc/git-current-commit)
                              :date (java.time.Instant/now)}
                              (when-let [repo (try (tc/git-remote)      (catch clojure.lang.ExceptionInfo _ nil))] {:repo repo})
                              (when-let [tag  (try (tc/git-nearest-tag) (catch clojure.lang.ExceptionInfo _ nil))] {:tag tag}))]
      (io/make-parents file-name)
      (with-open [w (io/writer (io/file file-name))]
        (pp/pprint deploy-info w)))
    (throw (ex-info ":deploy-info-file not provided" (into {} opts))))
  opts)

(defn check-release
  "Check that a release can be made from the current directory, using the provided opts. opts includes:

  :lib        -- opt: a symbol identifying your project e.g. 'org.github.pmonks/pbr
  :version    -- opt: a string containing the version of your project e.g. \"1.0.0-SNAPSHOT\"
  :dev-branch -- opt: the name of the development branch containing the changes to be PRed (defaults to \"dev\")"
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

  (let [git-status (tc/git :status "--short")]
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
  -- opts from the `deploy-info` task, if you wish to generate deploy-info --"
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
    (tc/git :fetch "origin" (str prod-branch ":" prod-branch))
    (tc/git :merge prod-branch)
    (tc/git :pull)

    (println "ℹ️ Checking that a release can be made...")
    (check-release opts)

    (println (str "ℹ️ All good; press any key to continue or Ctrl+C to abort..."))
    (flush)
    (read-line)

    (println "ℹ️ Tagging release as" (str version "..."))
    (tc/git :tag "-f" "-a" version "-m" (str ":gem: Release " version))

    (when deploy-info-file
      (println "ℹ️ Updating" (str deploy-info-file "..."))
      (deploy-info opts)
      (tc/git :add deploy-info-file)  ; Add the file just in case it's never existed before - this is no-op if it's already in the index
      (tc/git :commit "-m" (str ":gem: Release " version) deploy-info-file))

    (println "ℹ️ Pushing tag" version (str "(" (tc/git-tag-commit version) ")..."))
    (tc/git :push)
    (tc/git :push "origin" "-f" "--tags")

    (println "ℹ️ Creating 'release' pull request from" dev-branch " to " prod-branch "...")
    (let [pr-desc-fmt (get opts :pr-desc "%1$s release %2$s. See commit log for details of what's included in this release.")]
      (tc/exec ["hub" "pull-request" "--browse" "-f"
                "-m" (str "Release " version)
                "-m" (format pr-desc-fmt (str lib) (str version))
                "-h" dev-branch "-b" prod-branch]))

    (println "ℹ️ After the PR has been merged, it is highly recommended to:\n"
             "  1. git fetch origin" (str prod-branch ":" prod-branch) "\n"
             "  2. git merge" prod-branch "\n"
             "  3. git pull\n"
             "  4. git push")

    (println "⏹ Done."))
  opts)

(defn- get-scm-tag
  "Calculates the 'best' value for the <scm><tag> element in pom.xml."
  [opts]
  (if-let [tag (get-in opts [:pom :scm :tag])]
    tag
    (if-let [exact-tag (tc/git-exact-tag)]
      exact-tag
      (tc/git-current-commit))))

(defn pom
  "Generates a comprehensive pom.xml file. opts includes:

  :lib          -- opt: a symbol identifying your project e.g. 'com.github.yourusername/yourproject
  :version      -- opt: a string containing the version of your project e.g. \"1.0.0-SNAPSHOT\"
  :pom-file     -- opt: the name of the file to write to (defaults to \"./pom.xml\")
  :write-pom    -- opt: a flag indicating whether to invoke \"clojure -Spom\" after generating the basic pom (i.e. adding dependencies and repositories from your deps.edn file) (defaults to false)
  :validate-pom -- opt: a flag indicating whether to validate the generated pom.xml file after it's been constructed (defaults to false)
  :pom          -- opt: a map containing any other POM elements (see https://maven.apache.org/pom.html for details), using standard Clojure :keyword keys

See https://github.com/pmonks/tools-pom/ for more details"
  [opts]
  ; Always ensure there's a <tag> element inside the <scm> element - leaving it out causes build-clj to auto-populate
  ; it with an invalid value, which then breaks downstream tooling (e.g. cljdoc)
  ;
  ; See https://github.com/seancorfield/build-clj/issues/24
  (pom/pom (assoc-in opts [:pom :scm :tag] (get-scm-tag opts))))

(defn jar
  "Generates a library JAR for the project. opts are from https://github.com/seancorfield/build-clj/blob/main/src/org/corfield/build.clj#L171"
  [opts]
  (let [jar-opts (assoc opts :src-pom (get opts :pom-file "./pom.xml")
                             :tag     (get-scm-tag opts))]    ; Workaround for https://github.com/seancorfield/build-clj/issues/24
    (bb/jar jar-opts)))

(defn deploy
  "Builds and deploys the library's artifacts (pom.xml, JAR) to Clojars (or elsewhere), from the 'production' branch. opts includes:

  :prod-branch -- opt: the name of the production branch where the deployment is to be initiated from (defaults to \"main\")
  -- opts from the `pom task`, though note that :write-pom and :validate-pom are forced to true --
  -- opts from the `build-clj/jar` task --
  -- opts from the `build-clj/deploy` task (i.e. deps-deploy) --"
  [opts]
  (let [current-branch (tc/git-current-branch)
        main-branch    (get opts :prod-branch "main")]
    (if (= current-branch main-branch)
      (let [version     (tc/git-nearest-tag)
            deploy-opts (assoc opts :version      version
                                    :write-pom    true
                                    :validate-pom true)]
        (println "ℹ️ Deploying" (:lib deploy-opts) "version" (:version deploy-opts) "to Clojars.")
        (pom       deploy-opts)
        (jar       deploy-opts)
        (bb/deploy deploy-opts))
      (throw (ex-info (str "deploy task must be run from '" main-branch "' branch (current branch is '" current-branch "').") (into {} opts)))))
  opts)

(defn codox
  "Generates codox documentation. opts includes:

  :lib   -- opt: a symbol identifying your project e.g. 'org.github.pmonks/pbr
  :codox -- opt: a codox options map (see https://github.com/weavejester/codox#project-options). Note that PBR will auto-include the :source-uri option for com.github.* projects"
  [opts]
  (let [basis       (bb/default-basis)
        paths       (get basis :paths ["src"])
        github-url  (github-url (:lib opts))
        prod-branch (get opts :prod-branch "main")]
    (cx/generate-docs (merge {:source-paths paths}
                             (when github-url {:source-uri (str github-url "/blob/" prod-branch "/{filepath}#L{line}")})
                             (:codox opts))))
  opts)
