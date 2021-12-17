| | | |
|---:|:---:|:---:|
| [**main**](https://github.com/pmonks/pbr/tree/main) | [![Lint](https://github.com/pmonks/pbr/workflows/lint/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3Alint) | [![Dependencies](https://github.com/pmonks/pbr/workflows/dependencies/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3Adependencies) |
| [**dev**](https://github.com/pmonks/pbr/tree/dev)  | [![Lint](https://github.com/pmonks/pbr/workflows/lint/badge.svg?branch=dev)](https://github.com/pmonks/pbr/actions?query=workflow%3Alint) | [![Dependencies](https://github.com/pmonks/pbr/workflows/dependencies/badge.svg?branch=dev)](https://github.com/pmonks/pbr/actions?query=workflow%3Adependencies) |

[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/pbr)](https://clojars.org/com.github.pmonks/pbr/) [![Open Issues](https://img.shields.io/github/issues/pmonks/pbr.svg)](https://github.com/pmonks/pbr/issues) [![License](https://img.shields.io/github/license/pmonks/pbr.svg)](https://github.com/pmonks/pbr/blob/main/LICENSE)


<img alt="Ice cold can of hangover-inducing rubbish beer" align="right" width="25%" src="https://pabstblueribbon.com/wp-content/uploads/2020/10/pbr-org.png">

# PBR - Peter's Build Resources

A little [tools.build](https://github.com/clojure/tools.build) task library that supports the author's personal GitHub workflow.  It is not expected to be especially relevant for other developers' workflows.

If you're looking for the convenience functions, and the pom.xml and license tasks that used to be part of PBR, as of PBR v2.0 they've been refactored into their own micro-libraries to better facilitate reuse and contribution:

* [com.github.pmonks/tools-convenience](https://github.com/pmonks/tools-convenience/) - tools.build convenience fns
* [com.github.pmonks/tools-pom](https://github.com/pmonks/tools-pom/) - pom.xml-related build tasks
* [com.github.pmonks/tools-licenses](https://github.com/pmonks/tools-licenses/) - license-related build tasks

## Features

### API Documentation

[API documentation is available here](https://pmonks.github.io/pbr/).

### Build tasks

1. `deploy-info` - generate an EDN file containing deployment info for your code (build date/time and git commit SHA & (optionally) tag).
2. `release` - perform a release by tagging the current code, optionally updating the deploy-info.edn file, and creating a PR from a development branch to a production branch.
3. `deploy` - perform a deployment by constructing a comprehensive pom.xml file, building a JAR, and deploying them to clojars.

## Using the library

Express a maven dependency in your `deps.edn`, for a build tool alias:

```edn
 :aliases
    :build
      {:deps       {io.github.seancorfield/build-clj {:git/tag "v0.6.3" :git/sha "9b8e09b"}
                    com.github.pmonks/pbr            {:mvn/version "LATEST_CLOJARS_VERSION"}}
       :ns-default build}
```

Note that you must express an explicit dependency on `io.github.seancorfield/build-clj`, as that project [doesn't publish artifacts to Clojars yet](https://github.com/seancorfield/build-clj/issues/11), and transitive git coordinate dependencies are not supported by tools.deps.

### Requiring the namespace

In your build tool namespace(s):

```clojure
(ns your.build.namespace
  (:require [pbr.tasks :as pbr]))
```

### Worked example

For a worked example of using the library, see [futbot's build script](https://github.com/pmonks/futbot/blob/main/build.clj).

## FAQ

[//]: # (Comment: Every Question in this list has two spaces at the end THAT MUST NOT BE REMOVED!!)

**Q.** Why "PBR"?  
**A.** Because this code is cheap and nasty, and will give you a headache if you consume too much of it.

**Q.** Does PBR use itself for its own build tasks?  
**A.** Why yes it does!  You can see how it sneakily references itself [here](https://github.com/pmonks/pbr/blob/main/deps.edn#L32).

## Contributor Information

[Contributing Guidelines](https://github.com/pmonks/pbr/blob/main/.github/CONTRIBUTING.md)

[Bug Tracker](https://github.com/pmonks/pbr/issues)

[Code of Conduct](https://github.com/pmonks/pbr/blob/main/.github/CODE_OF_CONDUCT.md)

### Developer Workflow

The repository has two permanent branches: `main` and `dev`.  **All development must occur either in branch `dev`, or (preferably) in feature branches off of `dev`.**  All PRs must also be submitted against `dev`; the `main` branch is **only** updated from `dev` via PRs created by the core development team.  All other changes submitted to `main` will be rejected.

This model allows otherwise unrelated changes to be batched up in the `dev` branch, integration tested there, and then released en masse to the `main` branch, which will trigger automated generation and deployment of the release (Codox docs to github.io, JARs to Clojars, etc.).

## License

Copyright © 2021 Peter Monks

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
