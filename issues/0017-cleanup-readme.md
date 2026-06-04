# Clean up the README

**Phase:** 3
**Type:** dx

## Summary

The README currently mixes user-facing documentation with internal planning artifacts: phase descriptions, module
roadmaps, open questions, and "planned" module statuses. Strip all planning content. The README should answer only:
what is CloudMock, how to install it, how to use it, what services it supports today, what it does not do, and how to
contribute. Add Gradle and Maven dependency snippets and a minimal quickstart example. Move any planning, vision, or
roadmap content to the issues directory or CLAUDE.md.

## Acceptance criteria

- [x] The README contains no phase descriptions, roadmaps, "planned" module statuses, or open questions
- [x] A **What is CloudMock?** section explains the project in two to three sentences aimed at a developer who has never heard of it
- [x] An **Installation** section provides copy-paste Gradle and Maven dependency snippets for `cloudmock-core` and at least one service module
- [x] A **Quickstart** section shows the minimal test that starts CloudMock, sends a request, and asserts the response — under twenty lines of code
- [x] A **Supported services** section lists only the modules that are currently released and functional, with no "coming soon" or "planned" entries
- [x] A **Limitations / out of scope** section is present and matches the "Out of scope" block in CLAUDE.md
- [x] A **Contributing** section links to the issues directory and describes how to open a new module or report a bug
- [x] All planning content removed from the README is either already captured in an issue or moved to one before the README is changed

## Dependencies

None

## Notes

- The target reader is a developer who just landed on the GitHub page; assume no prior knowledge of the project.
- Dependency snippets should use a placeholder version (e.g. `0.1.0`) that is easy to find and replace at release time.
- The quickstart example should use `cloudmock-sqs` — it is the most commonly exercised module and the simplest to demonstrate.
- Vision and long-term direction belong in issue #0023, not here.
