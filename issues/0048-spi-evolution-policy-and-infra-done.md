# SPI evolution policy and a "done" bar for infrastructure issues

**Type:** design

## Summary

Two process/documentation gaps surfaced while building the state store and stateful handlers. Neither
is a code defect, but both will keep costing clarity if left unaddressed.

**1. The "frozen" SPI is not actually frozen — and should never claim to be.** `CLAUDE.md` and the
SPI javadoc describe the `StubRegistrar`/`CloudMockService` contract as "frozen," yet it has changed
twice during normal development: `register(StubRegistrar)` became `register(CloudMockContext)` for
the state store ([0024](0024-design-state-store-interface.md)/[0035](0035-implement-state-store.md)),
and the `StubHandler` overloads were added for stateful stubs ([0044](0044-stateful-stub-handlers.md)).
The "frozen" framing creates ceremony around changes that are routine and is simply inaccurate.

The intended stance is the opposite of "frozen": the SPI is **never declared closed**, not now and
not after 1.0. We deliberately retain the freedom to modify the contract whenever a real need arises,
rather than locking ourselves out of a better design. What replaces "frozen" is not a future freeze
date but an **evolution policy**: changes are allowed, handled through versioning and a clear
additive-vs-breaking distinction, and never blocked by a promise of permanence. The project also
still carries an open question about "versioning and compatibility policy between core and module JAR
versions" — that policy is exactly the mechanism that lets the SPI stay open to change safely.

**2. Infrastructure shipped as "done" without an end-to-end consumer.** [0035](0035-implement-state-store.md)
checked every acceptance box — the store was built, lifecycle-managed, persistent, thread-safe,
injected into modules, and exposed to the admin API — yet **nothing on the request path read or wrote
it**, so the server still served stateless responses. The gap was only found later and closed by
0044. The lesson is a process one: an infrastructure issue is not "done" when the plumbing exists; it
is done when a consumer exercises it end to end.

## Acceptance criteria

- [ ] The SPI documentation is reframed from "frozen" to an **evolution policy** that explicitly
  states the contract is never declared closed (including post-1.0): the SPI stays open to change, and
  flexibility to modify it is retained on purpose
- [ ] That policy spells out *how* change is managed rather than *whether* it is allowed: the
  additive-vs-breaking distinction and the `CloudMock-Core-Min-Version` compatibility story that make
  ongoing change safe
- [ ] The open question on "versioning and compatibility policy between core versions and module JAR
  versions" (in `CLAUDE.md` → Open questions) is resolved or folded into that policy
- [ ] Stale "frozen" references in `CLAUDE.md` and SPI javadoc (`StubRegistrar`, `CloudMockService`)
  are updated to match the policy
- [ ] A documented **definition of done for infrastructure issues**: no infra issue closes until a
  real consumer (a module, the admin API, or an end-to-end test) exercises it through the running
  system — capturing the lesson from the 0035→0044 gap
- [ ] No production code change is required by this issue; it is documentation and process only

## Dependencies

- [0044](0044-stateful-stub-handlers.md) (the change that exposed both gaps)
- [0035](0035-implement-state-store.md) (the infrastructure-without-consumer example)

## Notes

- This is intentionally a docs/policy issue, not code. It exists so the next SPI change and the next
  infrastructure issue inherit clear expectations rather than rediscovering them.
- Keep the policy short and enforceable — a paragraph in `CLAUDE.md`, not a governance document.
