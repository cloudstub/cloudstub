# Add Prettier workflow for documentation files formatting

**Type:** setup

## Summary

README files and markdown documents across the repository have no enforced formatting standard.
Add a Prettier workflow that checks markdown formatting on every pull request and provides a
format job that automatically fixes formatting issues.

## Acceptance criteria

- [ ] `.github/workflows/prettier.yml` added to the repository
- [ ] Workflow runs on push to `main` and on pull requests
- [ ] Check job fails the pull request if any markdown file is not formatted correctly
- [ ] Format job runs Prettier and commits the result automatically
- [ ] Prettier configuration file added to the repository root
- [ ] All existing README and markdown files pass Prettier on merge

## Notes

- Scope Prettier to markdown files only — Java formatting is handled separately by the existing
  build pipeline.
- The format job should only run manually or on push to `main`, not on pull requests, to avoid
  commit loops.
- A `.prettierignore` file should exclude generated files and the `issues/` directory — issue
  tickets are freeform and should not be auto-formatted.
