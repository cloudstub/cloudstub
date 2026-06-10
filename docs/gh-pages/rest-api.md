# REST API

When running in standalone mode CloudMock exposes a REST API on a secondary port (`4567` by default).
The API and the AWS mock port (`4566`) run in the same process — no extra server to start.

## Configuration

| Mechanism            | Example                   |
|----------------------|---------------------------|
| CLI flag             | `--api-port=9001`         |
| Environment variable | `CLOUDMOCK_API_PORT=9001` |
| Default              | `4567`                    |

Precedence: `--api-port` flag → `CLOUDMOCK_API_PORT` env var → default `4567`.

=== "CLI flag"

    ```
    java -jar cloudmock-standalone.jar --api-port=9001
    ```

=== "Environment variable"

    ```
    CLOUDMOCK_API_PORT=9001 java -jar cloudmock-standalone.jar
    ```

The API port is printed at startup alongside the mock port:

```
CloudMock started on port 4566
CloudMock API on port 4567
```

## Why a separate port?

AWS service stubs (especially S3) register broad catch-all path patterns. Serving the API on the
same port would cause those stubs to match `/api/*` paths before the API routes could. A dedicated
port keeps the two traffic streams cleanly separated.

The two ports are **two views of the same state**, not two separate mocks. The mock port speaks the
AWS wire protocol (for SDKs); the API port is a friendly JSON view for the console and CLI. For
state-backed modules they read and write one shared store, so a message sent through the AWS SDK is
returned by `GET /api/sqs/receive-message`, and vice versa.

## Endpoints

All responses are JSON. The base URL used in the examples below is `http://localhost:4567`.

---

### `GET /api/status`

Returns a snapshot of the running instance: ports, uptime, loaded modules with their registered
stubs, and the full list of available API routes.

**Response**

```json
{
  "port": 4566,
  "apiPort": 4567,
  "startedAt": "2026-06-06T10:00:00Z",
  "uptime": "PT5M30S",
  "modules": [
    {
      "id": "sqs",
      "stubs": [
        {
          "protocol": "JSON_TARGET",
          "matchKey": "AmazonSQS.SendMessage"
        },
        {
          "protocol": "JSON_TARGET",
          "matchKey": "AmazonSQS.ReceiveMessage"
        }
      ]
    }
  ],
  "routes": [
    {
      "method": "GET",
      "path": "/api/status",
      "description": "Running instance info: port, uptime, modules, routes"
    },
    {
      "method": "POST",
      "path": "/api/reset",
      "description": "Clear all state (or ?service=X for one service)"
    },
    {
      "method": "POST",
      "path": "/api/sqs/send-message",
      "service": "sqs",
      "command": "send-message",
      "description": "Send a message to an SQS queue",
      "params": [
        { "name": "queue", "required": true, "description": "Queue name" },
        { "name": "body", "required": true, "description": "Message body" }
      ]
    }
  ]
}
```

Use `GET /api/status` as the discovery endpoint — the `routes` array tells you exactly what
operations are available without consulting documentation. Module routes additionally carry a
`service`, a `command` name, and a `params` list (each with `name`, `required`, and `description`).
That metadata is what lets the [CLI](cli.md) build `clm <service> <command>` with typed options at
runtime, with no hardcoded knowledge of any module.

---

### `POST /api/reset`

Clears all state in the store. Use this to start each test scenario with a clean slate when
running tests against a long-lived standalone process.

A full reset (no `service`) also clears the request history. A single-service reset clears only
that service's state and leaves the history intact — the history is one shared journal with no
per-service partition.

=== "Clear everything"

    ```
    POST /api/reset
    ```

=== "Clear one service"

    ```
    POST /api/reset?service=sqs
    ```

**Response**

```json
{
  "status": "ok"
}
```

---

### `GET /api/history`

Returns the list of all requests served since startup, most recent first. Each entry shows
whether the request matched a registered stub, which service and operation handled it, and the
HTTP status code returned.

=== "All services"

    ```
    GET /api/history
    ```

=== "One service"

    ```
    GET /api/history?service=sqs
    ```

**Response**

```json
{
  "requests": [
    {
      "timestamp": "2026-06-06T10:01:00Z",
      "method": "POST",
      "url": "/?Action=SendMessage&...",
      "serviceId": "sqs",
      "operation": "AmazonSQS.SendMessage",
      "statusCode": 200,
      "matched": true
    },
    {
      "timestamp": "2026-06-06T10:00:55Z",
      "method": "GET",
      "url": "/unknown-path",
      "serviceId": null,
      "operation": null,
      "statusCode": 404,
      "matched": false
    }
  ]
}
```

Unmatched requests — those not handled by any registered stub — appear with `"matched": false`
and `"serviceId": null`. They are the most common source of integration issues; check the `url`
field to diagnose why a stub did not match.

The history is capped at the last 1000 requests by default so a long-lived process does not grow
without bound. Change the cap with `--max-history=<n>` (or `CLOUDMOCK_MAX_HISTORY`); pass
`--max-history=unlimited` to retain everything.

---

### `GET /api/openapi.json`

Returns an OpenAPI 3.0 spec auto-generated from all registered routes, including any routes
contributed by module-specific `CloudMockApiService` implementations.

```
GET /api/openapi.json
```

The spec updates automatically when modules are added or removed — no manual maintenance required.

## Module routes

Modules can expose their own routes under `/api/<serviceId>/…` by implementing the
`CloudMockApiService` SPI interface. If a module JAR is not on the classpath — or the module is
disabled with `--modules` — its routes do not exist; loading the module makes them available
automatically. Each route may advertise a `command` name and `params`, which the [CLI](cli.md)
turns into a `clm <service> <command>` subcommand.

Parameters are passed as query-string values (`POST /api/sqs/send-message?queue=q&body=hello`);
the request body is not read.

See [Module Authoring](module-authoring.md#8-exposing-cli-commands-via-the-rest-api) for details on
implementing `CloudMockApiService`.
