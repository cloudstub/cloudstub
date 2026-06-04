# Launch the public documentation site

**Phase:** 3
**Type:** dx

## Summary

Publish a documentation site covering the three audiences for CloudMock: end users (getting started, adding modules, writing tests), module authors (building a new module from scratch using the SPI contract), and tooling consumers (running the stub generation agent). This is the deliverable that makes CloudMock accessible to the open-source community beyond its GitHub README.

## Acceptance criteria

- [ ] **Developer quickstart** shows how to add `cloudmock-core` and one module as Gradle/Maven dependencies and write a first passing test, in under five minutes of reading
- [ ] **JUnit 6 extension guide** covers both `@ExtendWith` and `@RegisterExtension` usage patterns with working code examples
- [ ] **Module authoring guide** walks through building a new module from scratch: choosing the right protocol family, writing Handlebars response templates, registering via `META-INF/services`, and structuring the test suite — using `cloudmock-sqs` and `cloudmock-secretsmanager` as reference examples
- [ ] **Fault injection guide** documents all three annotations (`@SimulateThrottle`, `@SimulateTimeout`, `@SimulateNetworkBrownout`) with usage examples and an explanation of the stateless cleanup contract
- [ ] **Stub generation agent guide** explains how to run the codegen against a Smithy model file, what output to expect, and what manual review steps are required after generation
- [ ] All code examples in the documentation compile and run against the published release artifacts; a `docs-examples` Gradle subproject enforces this in CI
- [ ] Site is publicly accessible at a stable URL before the Phase 3 announcement

## Dependencies

0004, 0006, 0008, 0009, 0010, 0012

## Notes

- Technology choice for the site (MkDocs Material, Docusaurus, Writerside, etc.) is not prescribed — choose based on team familiarity and maintenance cost.
- The `docs-examples` subproject should declare dependencies on the published Maven coordinates (not project paths) to ensure the examples test the public artifact, not the local build.
- The module authoring guide is the highest-value page for project growth — prioritise it if time is tight.
