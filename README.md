[![Build](https://github.com/pmonks/pbr/workflows/build/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3Abuild) [![Lint](https://github.com/pmonks/pbr/workflows/lint/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3Alint) [![Dependencies](https://github.com/pmonks/pbr/workflows/dependencies/badge.svg?branch=main)](https://github.com/pmonks/pbr/actions?query=workflow%3Adependencies) [![Open Issues](https://img.shields.io/github/issues/pmonks/discljord-utils.svg)](https://github.com/pmonks/pbr/issues) [![License](https://img.shields.io/github/license/pmonks/discljord-utils.svg)](https://github.com/pmonks/pbr/blob/main/LICENSE)


<img alt="Ice cold can of hangover-inducing rubbish beer" align="right" width="25%" src="https://pabstblueribbon.com/wp-content/uploads/2020/10/pbr-org.png">

# PBR - Peter's Build Resources

A little library that extends Sean Corfield's [`build-clj`](https://github.com/IGJoshua/discljord) build tool library with:

1. Convenience functions for working with the command line
2. Additional build tasks

These can be used independently; use of the convenience functions does not require use of the build tasks, and vice versa.

## Features

### API Documentation

Coming soon.  For now, best to [browse the source](https://github.com/pmonks/pbr/tree/main/src) and/or make liberal use of the `doc` fn at the REPL.

### Convenience functions 

1. `exec` - more convenient / opinionated version of tools.build's [`process` function](https://clojure.github.io/tools.build/clojure.tools.build.api.html#var-process).
2. `ensure-command` - ensure that a binary exists for the given command (note: POSIX only).
3. `git` - easily invoke a git command.

### Build tasks

1. `deploy-info` - generate an EDN file containing deployment info for your code (build date/time and git commit & tag).
2. `pom` - generate a comprehensive `pom.xml` file from EDN (which can come from anywhere - stored in your `deps.edn` or a separate file, or synthesised on the fly in your build tool script).
3. `release` (coming soon) - perform a release by tagging the current code and creating a PR from `dev` branch to `main`. This is highly specific to the author's GitHub workflow and may not be useful for anyone else.

## Using the library

Express a git dependency in your `deps.edn`:

```edn
{:deps {org.github.pmonks/pbr {:git/url "https://github.com/pmonks/pbr.git"
                               :git/sha "LATEST_GIT_SHA"}}}   ; Note: best to use the latest SHA until such time as this is deployed to Clojars
```

### Requiring the namespace

In your namespace(s):

```clojure
(ns your.namespace
  (:require [org.pmonks.pbr :as pbr]))
```

Require either or both of the included namespaces at the REPL:

```clojure
(require '[org.pmonks.pbr :as pbr])
```

## Why "PBR"?

Because this code is cheap and nasty, and will give you a headache if you consume too much of it.

## Contributor Information

[Contributing Guidelines](https://github.com/pmonks/pbr/blob/main/.github/CONTRIBUTING.md)

[Bug Tracker](https://github.com/pmonks/pbr/issues)

[Code of Conduct](https://github.com/pmonks/pbr/blob/main/.github/CODE_OF_CONDUCT.md)

## License

Copyright © 2021 Peter Monks

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

SPDX-License-Identifier: [Apache-2.0](https://spdx.org/licenses/Apache-2.0)
