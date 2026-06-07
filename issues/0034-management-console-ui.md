# Management console web UI

**Type:** dx

## Summary

A lightweight web interface served alongside the admin REST API in standalone mode. The console gives developers
visual access to mock state without touching the CLI or writing code. It consumes only the admin REST API and
uses the `/api/status` discovery endpoint to build its navigation — when a module is added or removed, the
console reflects this automatically. The console should be polished and visually appealing — it is the public
face of CloudMock.

## Features

- Dashboard showing loaded modules, uptime, and request count
- Per-service resource browser, dynamically built from discovered module routes
- Payload inspection for messages, objects, and secrets with syntax highlighting
- Request history timeline with filtering by service and operation
- Ability to send test data through the API (push a message, upload an object, create a secret)
- Reset controls per service and globally
- Dark and light mode support

## Acceptance criteria

- [ ] Console served as a static SPA on the same port as the admin REST API
- [ ] Console consumes only the admin REST API — no direct state store access
- [ ] Navigation is built dynamically from `/api/status` — no hardcoded service list
- [ ] Adding or removing a module changes the console automatically
- [ ] Design is modern, clean, and professional — not a bare-bones admin panel
- [ ] Consistent visual language: typography, spacing, color, and component design
- [ ] Responsive layout that works on different screen sizes
- [ ] Dark and light mode
- [ ] Payload viewer supports JSON syntax highlighting and formatting
- [ ] Console works in any modern browser with no install
- [ ] Console is bundled into the standalone JAR — no separate build or deploy step
- [ ] No login, no setup — open the browser, see state

## Dependencies

- 0021 (standalone mode)
- 0024 (state store interface)
- 0032 (admin REST API)

## Notes

- Built with Angular. Bundled as static assets inside the JAR so standalone mode serves everything from a
  single artifact.
- Look at Grafana, Linear, and the Stripe dashboard as design references — developer tools that are both
  functional and well-crafted.
- PrimeNG are good component library options for a polished result without heavy custom CSS.
