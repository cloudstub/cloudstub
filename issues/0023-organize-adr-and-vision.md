# Move ADR to docs/adr directory

**Phase:** 3
**Type:** dx

## Summary

Move the existing `docs/adr.md` to `docs/adr/cloudmock-architecture.md`. The ADR is contributor-facing, not user-facing.

## Acceptance criteria

- [x] `docs/adr/001-cloudmock-architecture.md` exists and contains the content previously in `ADR.md`
- [x] The old `docs/adr.md` is removed
- [x] No vision or planning content remains in the README

## Dependencies

- #0017 (README must be cleaned up before vision content moves to its final home)

## Notes

- The file is contributor-facing and is not linked from the user-facing README or MkDocs nav.
- ADR files follow the `001-`, `002-`, `003-` numbering convention. Future architectural decisions should be added as the next number in the same directory.
