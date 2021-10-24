| | | | |
|---:|:---:|:---:|
| [**main**](https://github.com/pmonks/pbr/tree/main) | [![Lint](https://github.com/pmonks/pbr/workflows/lint/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3Alint) | [![Dependencies](https://github.com/pmonks/pbr/workflows/dependencies/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3Adependencies) |
| [**dev**](https://github.com/pmonks/pbr/tree/dev)  | [![Lint](https://github.com/pmonks/pbr/workflows/lint/badge.svg?branch=dev)](https://github.com/pmonks/pbr/actions?query=workflow%3Alint) | [![Dependencies](https://github.com/pmonks/pbr/workflows/dependencies/badge.svg?branch=dev)](https://github.com/pmonks/pbr/actions?query=workflow%3Adependencies) |

[![Latest Version](https://img.shields.io/clojars/v/com.github.pmonks/pbr)](https://clojars.org/com.github.pmonks/pbr/) [![Open Issues](https://img.shields.io/github/issues/pmonks/discljord-utils.svg)](https://github.com/pmonks/pbr/issues) [![License](https://img.shields.io/github/license/pmonks/discljord-utils.svg)](https://github.com/pmonks/pbr/blob/main/LICENSE)


<img alt="Ice cold can of hangover-inducing rubbish beer" align="right" width="25%" src="https://pabstblueribbon.com/wp-content/uploads/2020/10/pbr-org.png">

# PBR - Peter's Build Resources

A little library that extends Sean Corfield's [`build-clj`](https://github.com/IGJoshua/discljord) build tool library with:

1. Convenience functions for working with the command line
2. Additional build tasks

These can be used independently; use of the convenience functions does not require use of the build tasks, and vice versa.

## Features

### API Documentation

[API documentation is available here](https://pmonks.github.io/pbr/).

### Convenience functions 

1. `exec` - more convenient / opinionated version of tools.build's [`process` function](https://clojure.github.io/tools.build/clojure.tools.build.api.html#var-process).
2. `ensure-command` - ensure that a binary exists for the given command (note: POSIX only).
3. `git` - easily invoke a git command and obtain its output

### Build tasks

1. `deploy-info` - generate an EDN file containing deployment info for your code (build date/time and git commit SHA & (optionally) tag).
2. `pom` - generate a comprehensive `pom.xml` file from EDN (which can come from anywhere - stored in your `deps.edn` or a separate file, or synthesised on the fly in your build tool script).
3. `licenses` - attempt to display the licenses used by all transitive dependencies of the project
4. `release` - perform a release by tagging the current code, optionally updating the deploy-info.edn file, and creating a PR from a development branch to a production branch. This is quite specific to the author's GitHub workflow and may have limited utility for others.

## Using the library

Express a maven dependency in your `deps.edn`:

```edn
{:deps {com.github.pmonks/pbr {:mvn/version "LATEST_VERSION_ON_CLOJARS"}}}
```

### Requiring the namespaces

In your build tool namespace(s):

```clojure
(ns your.build.namespace
  (:require [pbr.tasks       :as pbr]
            [pbr.convenience :as pbrc]))
```

Require either or both of the included namespaces at the REPL:

```clojure
(require '[pbr.tasks       :as pbr])
(require '[pbr.convenience :as pbrc])
```

### Worked example

For a worked example of using the library, see [futbot's build script](https://github.com/pmonks/futbot/blob/main/build.clj).

## Why "PBR"?

Because this code is cheap and nasty, and will give you a headache if you consume too much of it.

## Contributor Information

[Contributing Guidelines](https://github.com/pmonks/pbr/blob/main/.github/CONTRIBUTING.md)

[Bug Tracker](https://github.com/pmonks/pbr/issues)

[Code of Conduct](https://github.com/pmonks/pbr/blob/main/.github/CODE_OF_CONDUCT.md)

### Developer Workflow

The `pbr` source repository has two permanent branches: `main` and `dev`.  **All development must occur either in branch `dev`, or (preferably) in feature branches off of `dev`.**  All PRs must also be submitted against `dev`; the `main` branch is **only** updated from `dev` via PRs created by the core development team.  All other changes submitted to `main` will be rejected.

This model allows otherwise unrelated changes to be batched up in the `dev` branch, integration tested there, and then released en masse to the `main` branch.

#### FAQ

Q. Does PBR use itself for build tasks?
A. Why yes it does!  You can see how it sneakily references itself [here](https://github.com/pmonks/pbr/blob/main/deps.edn#L31).

Q. How comprehensive is the license task?
A. While it makes a pretty good effort to find license information included in the published artifacts for a project's dependencies, and [falls back](https://github.com/pmonks/pbr/blob/data/fallbacks.edn) on manually verified information when necessary, this code is no substitute for a "real" software license compliance tool.

Q. The license task say "Unable to determine licenses for these dependencies" and then asks me to raise a bug report. Why?
A. If an artifact contains no identifiable license information, the logic falls back on a [manually maintained list of dependency -> licenses](https://github.com/pmonks/pbr/blob/data/fallbacks.edn).  That message appears when there is no identifiable license information in the artifact AND the dependency has no fallback information either.  By raising a bug including the deps(s) that the tool listed, you give the author an opportunity to add those dep(s) to manually determine the licenses for those dep(s) and update the fallback list accordingly.

Q. When the fallback list is updated, will I need to update my new version of PBR to get it?
A. No - the fallback list is retrieved at runtime, so any updates to it will be picked up soon after they are made by all versions of PBR.

Q. Doesn't that mean that PBR requires an internet connection in order to function?
A. Yes indeed.

## License

Copyright © 2021 Peter Monks

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
