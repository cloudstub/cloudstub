# Extract a shared StubTemplates.load helper and emit it from codegen

**Phase:** 3
**Type:** housekeeping

## Summary

Generated service modules (`cloudmock-s3`, `cloudmock-sns`, and every future REST/XML module) each carry an identical
private `loadTemplate(String)` method, emitted verbatim by the codegen (`ModuleGenerator.serviceClass()`). The duplication
is currently in two modules and will grow as DynamoDB, Lambda, and others are generated the same way. Extract the logic
into a single public helper in `cloudmock-core` and update the codegen to emit a call to it instead of re-inlining the
method body. SQS and Secrets Manager are unaffected — they inline string constants and do not use `loadTemplate`.

A static helper is preferred over an abstract base class: it avoids spending the module's single `extends` slot on a
utility, can be unit-tested in isolation, and keeps the generated service class focused on registrations + templates
(which is what matters in review).

## Acceptance criteria

- [ ] A public `StubTemplates.load(Class<?> anchor, String name)` helper exists in `cloudmock-core`, returning the
      trimmed UTF-8 template contents and throwing a clear exception when the resource is missing (same behaviour as the
      current generated `loadTemplate`)
- [ ] `StubTemplates` has unit tests covering: successful load, missing-template error, and trimming
- [ ] `ModuleGenerator` emits `StubTemplates.load(<ServiceClass>.class, name)` at each registration site and no longer
      generates the private `loadTemplate` method body
- [ ] `cloudmock-s3` and `cloudmock-sns` are regenerated (or hand-updated to match) to use the helper; their existing
      tests still pass
- [ ] `./gradlew build` passes
- [ ] Module isolation still holds — the helper lives in `cloudmock-core`, which modules already depend on; no module
      gains a dependency on another module

## Dependencies

- #0019 (cloudmock-s3 — first generated module using `loadTemplate`)
- #0020 (cloudmock-sns — second generated module using `loadTemplate`)
- #0012 (codegen tool)

## Notes

- The helper belongs in the public surface of `cloudmock-core` (e.g. `io.cloudmock.core.spi` or a new
  `io.cloudmock.core` util), since generated modules are external consumers of it.
- Related but **out of scope** (consider a follow-up): the generated `loadTemplate` resolves templates via an
  *absolute* classpath path (`/templates/<name>.hbs`), which is classloader-wide. With all modules on one classpath,
  two modules shipping the same template filename would collide on first-match. Namespacing templates per service
  (`/templates/<serviceId>/<name>.hbs`) would close this — worth doing in the same codegen pass if cheap, but it is a
  separate concern from the dedup and can be its own issue.
- Keep the helper's failure mode identical to today: missing template → fail fast with the resource path in the message
  (currently `IllegalStateException`), I/O error → `UncheckedIOException`.
