# Verify version compatibility and rename cloudmock-junit6 to cloudmock-junit

**Type:** core

## Summary

CloudMock uses JUnit 6 internally but consumers may be on JUnit 5. The `cloudmock-junit6` module
must not force a JUnit version on downstream projects. Verify that the module declares JUnit as
a `compileOnly` dependency so consumers bring their own JUnit version without classpath conflicts.
Fix the dependency declaration if needed and validate that a JUnit 5 project can use the module
without issues. If both versions are confirmed working, rename the module to `cloudmock-junit`
to reflect broader compatibility. JUnit 4 is explicitly not supported.

## Acceptance criteria

- [ ] JUnit dependency declared as `compileOnly` — not `implementation`
- [ ] A JUnit 5 project can use the module without pulling in two JUnit versions
- [ ] A JUnit 6 project can use the module without classpath conflicts
- [ ] If any JUnit 6 specific API is used that does not exist in JUnit 5, a new issue is created
  to fix the incompatibility before the rename proceeds
- [ ] Both versions confirmed working — module renamed from `cloudmock-junit6` to `cloudmock-junit`
- [ ] All internal references updated — `settings.gradle`, `build.gradle`, imports, CLAUDE.md
- [ ] `cloudmock-example` updated to demonstrate both JUnit 5 and JUnit 6 usage
- [ ] Documentation states supported versions are JUnit 5 and JUnit 6
- [ ] Documentation states JUnit 4 is not supported

## Notes

- The rename only happens after both versions are confirmed working.
