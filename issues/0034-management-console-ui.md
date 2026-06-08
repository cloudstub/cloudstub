# CloudMock web UI

**Type:** dx

## Summary

A lightweight web interface that connects to a running CloudMock standalone instance over the
REST API. The console gives developers visual access to mock state without touching the CLI or
writing code. It uses the `/api/status` discovery endpoint to build its navigation — when a
module is added or removed, the console reflects this automatically. The console lives in a
separate repository `cloud-mock/cloudmock-console` and is an optional install, not part of
the core distribution. It should be polished and visually appealing — it is the public face
of CloudMock.

## Features

- Dashboard showing loaded modules, uptime, and request count
- Per-service resource browser, dynamically built from discovered module routes
- Payload inspection for messages, objects, and secrets with syntax highlighting
- Request history timeline with filtering by service and operation
- Ability to send test data through the REST API (push a message, upload an object, create a secret)
- Reset controls per service and globally
- Dark and light mode support

## Acceptance criteria

- [x] Console lives in a separate repository `cloud-mock/cloudmock-console`
- [x] Console consumes only the CloudMock REST API — no direct state store access
- [x] Navigation is built dynamically from `/api/status` — no hardcoded service list
- [x] Adding or removing a module changes the console automatically
- [x] Design is modern, clean, and professional — not a bare-bones admin panel
- [x] Consistent visual language: typography, spacing, color, and component design
- [x] Responsive layout that works on different screen sizes
- [x] Dark and light mode
- [x] Payload viewer supports JSON syntax highlighting and formatting
- [x] Works in any modern browser with no install
- [x] No login, no setup — open the browser, point it at your CloudMock instance, see state

## Dependencies

- 0021 (standalone mode)
- 0024 (state store interface)
- 0032 (REST API)

## Notes

- Built with Angular and PrimeNG.
- Separate repository means the console can be versioned independently from CloudMock core.
  As long as the REST API contract does not change, any console version works with any
  CloudMock version.
- Look at Grafana, Linear, and the Stripe dashboard as design references — developer tools
  that are both functional and well-crafted.
