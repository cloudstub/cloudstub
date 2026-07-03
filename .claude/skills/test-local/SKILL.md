---
name: test-local
description: Test an implemented CloudStub service module end-to-end in the standalone server through the real distribution path (e.g. "/test-local dynamodb"). Publishes the module to mavenLocal, serves it over HTTP, and has cloudstub-local auto-download and load the published jar. Use when asked to verify a module works standalone, from mavenLocal, or as a real consumer would install it.
---

Validate a `cloudstub-<service>` module the way a real user installs it: the standalone
`cloudstub-local` server **auto-downloads the published module jar** from a Maven repository into its
plugin directory, checksum-verifies it, loads it, and serves the AWS protocol. Here the repository is
your local `~/.m2` (mavenLocal), so you test the exact artifact you just built.

The driver is `.claude/skills/test-local/test-local.sh <serviceId>`. It is service-agnostic: it
proves the provisioning + load path. Exercise the service's actual operations afterward with
`run-cloudstub/smoke.sh`.

## Prerequisites

- Java 17+ and `python3` on `$PATH`.
- **The module must be publishable.** Services publish only once rolled out: add `cloudstub-<service>`
  to `publishedServices` in `gradle/modules.gradle` and add a matching `pomInfo` entry in
  `gradle/publishing.gradle`. Without both, `publishToMavenLocal` creates no publication and the
  script fails with a pointer to this step. Adding it here is the "publishing validated" gate — only
  do it when the module is otherwise complete.

## Run

```
./.claude/skills/test-local/test-local.sh <serviceId>            # publish + serve + auto-download + verify
./.claude/skills/test-local/test-local.sh <serviceId> --no-publish   # reuse what is already in ~/.m2
```

What it does:

1. `publishToMavenLocal` for `cloudstub-<service>` and `cloudstub-core` (version from `gradle.properties`).
2. Generates `.sha512/.sha256/.sha1` next to the published jar. mavenLocal installs omit checksums, but the downloader (`ChecksumVerifier`) requires one and fails fast without it — a real remote repo has them.
3. Builds the standalone fat jar (`:cloudstub-local:shadowJar`).
4. Serves `~/.m2/repository` with `python3 -m http.server` (the downloader is HTTP-only; `file://` is not supported).
5. Starts the server against an **empty** `--modules-dir` with `--maven-base-url` pointed at the served repo and `--services=<service>`, forcing an auto-download rather than using a local jar.
6. Asserts the module jar landed in the empty plugin dir and that `<service>` appears in `GET /api/status`.
7. Cleans up both background processes on exit.

## Exercise the service after the path is validated

```
./.claude/skills/run-cloudstub/smoke.sh --services=<service>
```

or drive it directly on the mock port (`4566` by default) with the AWS CLI / curl and the REST API on
the API port (`4567`).

## Notes

- The fat jar's built-in `CoreVersion` sets the default download version, which matches the version you publish to mavenLocal — so the exact jar is fetched, no `maven-metadata` resolution needed.
- The generated checksum files in `~/.m2` are harmless throwaway cache files; leave or delete them.
- This complements the in-repo `:cloudstub-local:integrationTest` (which loads local jars): here the module travels the full publish → resolve → download → checksum → load pipeline.
