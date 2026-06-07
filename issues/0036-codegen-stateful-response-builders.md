# Refactor cloudmock-codegen for stateful response generation

**Type:** core

## Summary

The codegen tool was built for stateless stub generation — it reads Smithy models and produces Handlebars
templates that echo request fields back in the response. For stateful simulation, modules need more than
templates. They need typed response builders they can populate with real data from the state store and
have serialised correctly into the AWS response format.

Refactor the codegen tool to generate two artefacts per operation into the module skeleton:

- The existing stub template for contract-level mocking (unchanged)
- A typed response builder that a module can instantiate, populate from store data, and return as a
  correctly serialised AWS response

The builders are generated into the module (not into `cloudmock-codegen` or `cloudmock-core`) — they are
consumer code that module authors compile and extend.

## Acceptance criteria

- [ ] Existing stub template generation is unchanged — contract-level modules are not affected
- [ ] Each operation gets a generated typed response builder matching its Smithy output shape
- [ ] Response builders handle serialisation to the correct wire format (JSON or XML) automatically
- [ ] Required fields are enforced at compile time — module authors cannot forget them
- [ ] Optional fields have sensible defaults where Smithy defines them
- [ ] Generated builders have no dependency on WireMock types
- [ ] Existing generated modules compile without changes after the refactor

## Dependencies

- [0024](0024-design-state-store-interface.md) (state store design — response builders are consumed by
  stateful module code that reads from the store)
- [0035](0035-implement-state-store.md) (state store implementation — must exist before stateful modules
  can be built and tested against it)

## Notes

- The goal is that a module author writing stateful SQS support pulls messages from the store and hands
  them to a generated `ReceiveMessageResponse.builder()` — they never touch raw JSON or XML.
- Smithy defines required vs optional fields, data types, and wire format. The generated builder should
  enforce all of this so module authors cannot produce a malformed response.
- The two-artefact approach (template + builder) means the same Smithy model serves both the stateless
  and stateful case. Modules opt into the builder when they add stateful support.
