# Console

The CloudMock Console is a web interface for a running [standalone](standalone.md) CloudMock
instance. It gives you visual access to mock state — loaded modules, request history, and per-service
operations — without touching the terminal or writing code. Open it in a browser, point it at your
instance, and you see what the mock is doing.

Like the [CLI](cli.md), the console is a **thin client over the [REST API](rest-api.md)**. It has no
dependency on CloudMock internals, WireMock, or any service module — it only speaks HTTP to
`/api/status` and `/api/<service>/…`. It builds its navigation at runtime from `GET /api/status`, so
an instance with more modules loaded simply shows more services; the console itself never changes.

## Install

The console ships in its own repository,
[cloud-mock/cloudmock-console](https://github.com/cloud-mock/cloudmock-console) — it is released
independently and is an optional install, not part of the core distribution. It is an Angular
application; building it requires Node.js 20+.

```
git clone https://github.com/cloud-mock/cloudmock-console
cd cloudmock-console
npm install
```

=== "Development server"

    ```
    npm start
    # serves on http://localhost:4200 with live reload
    ```

=== "Production build"

    ```
    npm run build
    # static bundle in dist/ — serve it with any static file server
    ```

The console is a static single-page app: once built it works in any modern browser with no install
and no backend of its own.

## Connecting

The console talks only to the REST API port. The default target is `http://localhost:4567`, which
matches standalone mode, so with a server on the default port no configuration is needed. Use the
**Connect** page to point it at a different host or port; the value is stored in the browser's
local storage.

!!! note "CORS"
    Because the console runs in the browser and calls the API from a different origin, the standalone
    REST API sends permissive CORS headers (`Access-Control-Allow-Origin: *`). No proxy is required.

If the instance is not reachable, the console shows a clear "Disconnected" state and a prompt to
configure the connection.

## Features

The console polls `GET /api/status` every few seconds, so the dashboard and navigation stay current
as modules load or state changes.

- **Dashboard** — uptime, mock and API ports, the loaded modules, and the number of stubs each
  registers. Reset all state or a single service with one click.
- **Request history** — every request captured by the mock, filterable by service, with method and
  status shown at a glance. Backed by [`GET /api/history`](rest-api.md).
- **Service browser** — one panel per module, **built dynamically from the module's routes**. Each
  route advertised by `/api/status` becomes a form: fill in its parameters, send it, and the
  response is shown with JSON syntax highlighting. For state-backed modules (SQS) the response is
  live data, so a message your app sent through the AWS SDK shows up here.
- **Dark and light mode** — follows your system preference and can be toggled; the choice is
  remembered.

## How new modules appear automatically

Because everything is driven by `/api/status`, the console needs no change when a module is added:

- A module that implements [`CloudMockApiService`](module-authoring.md#8-exposing-cli-commands-via-the-rest-api)
  contributes routes under `/api/<service>/…`, each advertising a command name and parameters.
- Start CloudMock with that module on the classpath and a panel for it appears in the service
  browser, with a form per route.
- Restrict the loaded modules with `--modules=<a,b>` and the console shows only those services — a
  service that is not loaded has no panel at all.

!!! note "One state, two views"
    The console and the AWS SDK read the **same state store**, just through different ports. The mock
    port speaks the AWS wire protocol; the API port (which the console uses) is a friendly JSON view of
    the same data. So for state-backed modules, `receive-message` in the console returns the message
    your app sent through the SDK. State-backing rolls out per module — SQS is live; S3 and Secrets
    Manager API commands are still synthetic until their own state-backing lands.
