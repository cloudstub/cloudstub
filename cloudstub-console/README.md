# CloudStub Console

Web interface for [CloudStub](https://github.com/cloudstub/cloudstub).
Inspect mock state, browse resources, and send test data from the browser
without writing code or touching the CLI.

Requires a running [CloudStub standalone instance](https://cloudstub.github.io/cloudstub/standalone/).

## Features

- Dashboard showing loaded modules, uptime, and request count
- Per-service resource browser built dynamically from the running instance
- Payload inspection with JSON syntax highlighting
- Request history with filtering by service and operation
- Send test data directly from the UI
- Reset state per service or globally
- Dark and light mode

## How it works

The console connects to a running CloudStub standalone instance over the REST API.
It calls `/api/status` on startup to discover which modules are loaded and builds
its navigation dynamically. Adding a module to CloudStub adds it to the console
automatically — no console update required.

## Requirements

- [Node.js](https://nodejs.org/) 20.19+ (required by Angular 21)
- npm 11+
- A running CloudStub standalone instance
- Any modern browser

## Getting started

Install dependencies:

```bash
npm install
```

Start a CloudStub standalone instance so the console has something to connect to.
Follow the [standalone guide](https://cloudstub.github.io/cloudstub/standalone/);
by default it listens on `http://localhost:4567`, which is the host the console
connects to out of the box. You can point the console at a different instance from
the **Connect** screen in the UI.

## Running the project

Start the development server:

```bash
npm start
```

This runs `ng serve`. Open `http://localhost:4200/` in your browser — the app
reloads automatically when you change source files.

## Building

Create a production build:

```bash
npm run build
```

Build artifacts are written to the `dist/` directory, optimized for performance.

To rebuild continuously during development:

```bash
npm run watch
```

## Testing

Unit tests run with the [Vitest](https://vitest.dev/) test runner via the Angular CLI:

```bash
npm test
```

This executes `ng test`. Spec files live next to the code they cover as
`*.spec.ts` files under `src/`.

## Project structure

```
src/app/
  core/services/   API client and shared services (e.g. cloudstub-api)
  pages/           Route-level pages (e.g. connect, dashboard)
public/            Static assets
```

## Built with

- Angular
- PrimeNG

## Status

_In development._

## License

[Apache 2.0](https://github.com/cloudstub/cloudstub-console/blob/main/LICENSE)
