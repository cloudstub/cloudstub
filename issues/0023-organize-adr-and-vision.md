# Move ADR and create VISION in docs/adr directory

**Phase:** 3
**Type:** dx

## Summary

Move the existing `ADR.md` to `docs/adr/cloudmock-architecture.md`. Create `docs/adr/project-vision.md` covering long-term
project direction: the three stages (contract → stateful → behavioural fidelity), standalone mode, SDK v1 support,
management console, and the LocalStack alternative goal. The ADR captures decisions already made. The project vision captures
where the project is going. Both are contributor-facing, not user-facing.

## Acceptance criteria

- [ ] `docs/adr/cloudmock-architecture.md` exists and contains the content previously in `ADR.md`
- [ ] `docs/adr/project-vision.md` exists and covers:
  - The three stages: Stage 1 (contract mocking — current), Stage 2 (stateful simulation), Stage 3 (behavioural fidelity — opt-in)
  - Standalone mode as a near-term goal for replacing LocalStack in local dev workflows
  - SDK v1 support via the companion library
  - Management console as a future goal for inspecting mock state
  - The LocalStack alternative positioning
- [ ] The old `ADR.md` is removed from the project root
- [ ] No vision or planning content remains in the README
- [ ] No duplication between the two files — ADR covers past decisions, VISION covers future direction

## Dependencies

- #0017 (README must be cleaned up before vision content moves to its final home)

## Notes

- Both files are contributor-facing. They are not linked from the user-facing README.
- The ADR file name follows the standard ADR numbering convention (`001-`). Future architectural decisions should be
  added as `002-`, `003-`, etc. in the same directory.
- VISION.md should be clearly framed as aspirational; no dates or version numbers should be included.
- Stateful simulation and behavioural fidelity are out of scope for current development — VISION.md should say this
  explicitly so contributors do not file issues against missing stateful behaviour.
