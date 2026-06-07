# Add Spotless for Java code formatting

**Type:** setup

## Summary

There is no enforced Java formatting standard in the repository. Add Spotless to the Gradle build
to automatically format Java code and fail CI if unformatted code is pushed. This keeps the
codebase consistent without relying on individual editor settings.

## Acceptance criteria

- [ ] Spotless plugin added to the root Gradle build
- [ ] Google Java Format configured as the formatting standard
- [ ] `./gradlew spotlessApply` reformats all Java source files
- [ ] `./gradlew spotlessCheck` fails if any Java source file is unformatted
- [ ] CI pipeline runs `spotlessCheck` on every pull request
- [ ] All existing Java source files pass Spotless on merge
- [ ] Import ordering enforced — unused imports are flagged

## Notes

- Apply Spotless to all submodules from the root build configuration so module authors
  do not need to configure it per module.
- `spotlessApply` should be documented in CLAUDE.md as a step to run before pushing.
- Exclude generated sources from Spotless — codegen output should not be reformatted
  by the formatter as it may break template structure.
