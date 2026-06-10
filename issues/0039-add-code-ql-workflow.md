# Add explicit CodeQL workflow

**Type:** setup

## Summary

GitHub's default CodeQL setup is configured on `main` but not consistently found during PR scans,
producing a warning on every pull request. Replace the default setup with an explicit CodeQL workflow
file so the configuration is version-controlled, consistent across all branches, and not dependent
on GitHub's default setup settings.

## Acceptance criteria

- [x] `.github/workflows/codeql.yml` added to the repository
- [x] Workflow configured for Java/Kotlin
- [x] Workflow runs on push to `main`, on pull requests, and on a weekly schedule
- [x] Default setup disabled in repository settings after the workflow is in place
- [ ] No CodeQL warnings on pull requests after the change

## Notes

- The weekly scheduled scan catches vulnerabilities introduced by dependency updates even without
  a code change.
- CodeQL should run on the same Java version used by the rest of the CI pipeline.
