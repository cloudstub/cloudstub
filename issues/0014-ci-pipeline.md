# CI pipeline

**Phase:** 1
**Type:** setup

## Summary

Add a CI pipeline (GitHub Actions) that runs on every push and pull request to `main`. The pipeline must compile all subprojects, run all tests, and fail if the module isolation constraint is violated.

## Acceptance criteria

- [ ] A GitHub Actions workflow file (`.github/workflows/ci.yml`) is added
- [ ] The workflow triggers on `push` and `pull_request` targeting `main`
- [ ] The workflow runs `./gradlew build` from the repository root
- [ ] The workflow fails if the module isolation constraint is violated (this is already enforced by the build; the pipeline just needs to run it)
- [ ] Java 17 is used in the CI environment

## Dependencies

- #0001 (Gradle monorepo setup must be in place)

## Notes

- Use the `actions/setup-java` action with `distribution: temurin` and `java-version: 17`.
- Cache the Gradle wrapper and dependency cache with `actions/cache` or the built-in Gradle caching in `gradle/actions/setup-gradle` to keep builds fast.
- No deployment or publish step is needed at this stage — compile and test only.