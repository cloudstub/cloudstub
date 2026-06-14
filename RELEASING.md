# Releasing

How to publish CloudStub artifacts to Maven Central (Sonatype Central Portal).

## Prerequisites (one-time)

- A Sonatype Central account with the `io.github.cloudstub` namespace verified.
- A PGP key whose **public** half is published to a keyserver (e.g. `keyserver.ubuntu.com`).
- Four encrypted secrets set in the GitHub repository (Settings → Secrets and variables → Actions):
  - `ORG_GRADLE_PROJECT_mavenCentralUsername` / `ORG_GRADLE_PROJECT_mavenCentralPassword` — a Central **user token** (Portal → Account → Generate User Token), not the web login.
  - `ORG_GRADLE_PROJECT_signingInMemoryKey` — the ASCII-armored private key (`gpg --armor --export-secret-keys <KEY_ID>`).
  - `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` — the key's passphrase.

## What gets published

Module classification lives in `gradle/modules.gradle`. Published: the library modules (`cloudstub-junit`, `cloudstub-testing`, `cloudstub-sdk-v1`, and each service in `publishedServices`) and the shadow/tool modules (`cloudstub-core`, `cloudstub-local`, `cloudstub-codegen`). Examples and scaffolding-only modules (`cloudstub-dynamodb`, `cloudstub-lambda`) are excluded. Each published module ships a primary JAR, a `-sources` JAR, a `-javadoc` JAR, and a signed POM.

To publish another service, add it to `publishedServices` (and its entry to `pomInfo`) in `gradle/publishing.gradle`.

## Version

The build defaults to `0.1.0-SNAPSHOT`. Override it at release time with `-Pversion`:

- Pre-release: `-Pversion=0.1.0-beta.1` (then `0.1.0-beta.2`, …).
- Stable: `-Pversion=0.1.0`.

Central rejects `-SNAPSHOT` for releases. Maven orders `0.1.0-beta.1 < 0.1.0`, so a stable release supersedes the betas. Publishing with the default `-SNAPSHOT` version instead routes to the Portal snapshot repository.

## Procedure

### Tag-driven release (primary)

Push a version tag; the `.github/workflows/release.yml` workflow picks it up, derives the version by
stripping the leading `v`, and runs `publishAndReleaseToMavenCentral`:

```
git tag v0.1.0-beta.1
git push origin v0.1.0-beta.1
```

### Manual fallback

If CI is unavailable, run from a machine that has the credentials in `~/.gradle/gradle.properties`
or as `ORG_GRADLE_PROJECT_*` environment variables:

1. Upload to a staging deployment, then review and release it manually in the Portal:

   ```
   ./gradlew publishToMavenCentral -Pversion=0.1.0-beta.1
   ```

   Review and release the deployment at https://central.sonatype.com.

2. Or upload and release in a single step:

   ```
   ./gradlew publishAndReleaseToMavenCentral -Pversion=0.1.0-beta.1
   ```

A released version is permanent: it cannot be deleted or overwritten.

## Local smoke test

```
./gradlew publishToMavenLocal
```

Writes every published module to `~/.m2`. Signing runs only when a key is configured; without one it is skipped.
