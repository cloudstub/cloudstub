# Fix gaps in stub generation agent (issue #0012 follow-up)

**Phase:** 3
**Type:** bug / enhancement

## Summary

Three gaps were identified during review of the `cloudmock-codegen` implementation against the
acceptance criteria in issue #0012. This issue tracks their resolution before the agent can be
considered complete.

## Acceptance criteria

- [ ] **Finding 1 — Separate template files:** For each operation, the generator writes a dedicated
  Handlebars template file (e.g. `src/main/resources/templates/<OperationName>.hbs`) instead of
  inlining the template as a Java string constant. The service class loads the template from the
  classpath at registration time (consistent with the AC wording: "one Handlebars response template
  file per operation").
- [ ] **Finding 2 — Generator unit tests:** A `src/test` directory exists in `cloudmock-codegen`
  with at least one test that feeds a known `.smithy` fixture model through `ModuleGenerator` and
  asserts: correct file count, correct protocol routing, presence of a `META-INF/services` entry,
  and at least one `register*Stub` call per operation in the generated service class.
- [ ] **Finding 3 — Published-artifact coordinates in generated `build.gradle`:** The generated
  `build.gradle` references `cloudmock-core` via published Maven coordinates
  (`implementation 'io.cloudmock:cloudmock-core:VERSION'`) rather than `project(':cloudmock-core')`,
  so the generated module compiles outside the monorepo. The version should be substituted from a
  constant or passed as a CLI flag (`--core-version`).

## Dependencies

0012

## Notes

- Finding 1 requires a corresponding change in how `StubRegistrar` loads templates if templates
  become classpath resources — verify the existing `registerJsonTargetStub` / `registerXmlFormStub`
  / `registerRestStub` signatures already accept a template string (they do), so the generated
  service class will need a small `loadTemplate(String name)` helper that reads from the classpath.
- For Finding 2, use a minimal hand-authored `.smithy` fixture (JSON protocol, 2–3 operations) kept
  under `src/test/resources/fixtures/`. Do not use real AWS Smithy models as test fixtures.
