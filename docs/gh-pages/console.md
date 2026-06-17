# Console

The CloudStub Console is a web interface for a running [standalone](standalone.md) CloudStub
instance — visual access to mock state (loaded modules, request history, per-service operations)
without the terminal or writing code.

It is **served by the standalone server itself**: start `cloudstub-local` and open
**`http://localhost:4567/console`** in a browser (the API port — opening `http://localhost:4567/`
redirects there). There is no separate app to install or host.

The console is a **thin client over the [REST API](rest-api.md)** — it only speaks HTTP to
`/api/status` and `/api/<service>/…`, building its navigation at runtime from `GET /api/status`, so
an instance with more modules loaded simply shows more services; the console itself never changes.

## Open it

Start the server (see [Standalone Mode](standalone.md)) and browse to the console:

```
java -jar cloudstub-local.jar --services=sqs
# then open http://localhost:4567/console
```

The console is bundled in the `cloudstub-local` distributable, so there is nothing else to download.
Because it is served from the same origin as the API it calls, no connection setup is needed — it
talks to the instance that served it.

!!! note "Developing the console"
    The console source lives in this monorepo under `cloudstub-console` (an Angular app). You only
    need to build it to work on the console itself; the standalone REST API also sends permissive CORS
    headers, so a `npm start` dev server on another port can call a running instance directly.

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

- A module that implements [`CloudStubApiService`](module-authoring.md#8-exposing-rest-api-routes)
  contributes routes under `/api/<service>/…`, each advertising a command name and parameters.
- Start CloudStub with that module on the classpath and a panel for it appears in the service
  browser, with a form per route.
- Restrict the loaded services with `--services=<a,b>` and the console shows only those services — a
  service that is not loaded has no panel at all.

!!! note "One state, two views"
    The console and the AWS SDK read the **same state store**, just through different ports. The mock
    port speaks the AWS wire protocol; the API port (which the console uses) is a friendly JSON view of
    the same data. So for state-backed modules, `receive-message` in the console returns the message
    your app sent through the SDK. State-backing rolls out per module — SQS is live; S3 and Secrets
    Manager API commands are still synthetic until their own state-backing lands.
