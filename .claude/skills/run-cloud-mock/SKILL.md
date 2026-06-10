---
name: run-cloud-mock
description: Run, start, build, smoke-test, or screenshot the CloudMock standalone server. Use when asked to launch CloudMock, verify the server works, test AWS mock endpoints, or check the REST API.
---

CloudMock standalone is a long-running Java server process. The driver is
`.claude/skills/run-cloud-mock/smoke.sh` — it starts the server on ephemeral
test ports, exercises every AWS protocol and every REST API route, then stops
it cleanly.

## Documentation style

Any documentation you write or touch — javadoc, inline comments, reference docs — describes only the actual behavior of
the code (what it does, how to use it, parameters, contracts, caveats). No narrative: no project history, issue-number
storytelling, design-philosophy rationale, or marketing framing ("reference implementation", "canonical example", "the
lesson is"). Inline comments may explain a non-obvious *why* for a specific line when it prevents a bug, but must not
editorialize. Rationale belongs in commits and issues, not in the code's documentation.

## Prerequisites

Java 17+ on `$PATH`. The project ships Java 21 in the dev environment.

```
java -version   # must print 17 or higher
```

## Build

The standalone fat JAR must be built before running:

```
./gradlew :cloudmock-standalone:shadowJar
# output: cloudmock-standalone/build/libs/cloudmock-standalone.jar
```

Or pass `--build` to the smoke script and it will build first.

## Run (agent path)

```bash
# From the repo root:
.claude/skills/run-cloud-mock/smoke.sh
```

Options:

- `--build` — build the JAR before starting
- `--port=N` — override mock port (default 14566)
- `--api-port=N` — override API port (default 14567)
- `--modules=sqs,sns` — start with a subset of modules

The script:

1. Starts the server in the background and waits up to 10 s for it to become ready
2. Tests `GET /api/status`, `GET /api/openapi.json`
3. Tests SQS (JSON/X-Amz-Target), Secrets Manager (JSON/X-Amz-Target), SNS (XML/form-URL)
4. Tests `GET /api/history`, `POST /api/reset`
5. Kills the server on exit (clean or error)

Server log is written to `/tmp/cloudmock-smoke.log` — check it if startup fails.

## Run (human path)

```bash
java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar
# mock on :4566, API on :4567 — Ctrl-C to stop
```

Port overrides:

```bash
java -jar cloudmock-standalone/build/libs/cloudmock-standalone.jar \
  --port=4566 --api-port=4567 --modules=sqs,sns
```

## REST API endpoints

All on the API port (default 4567):

| Method | Path                       | Purpose                                            |
|--------|----------------------------|----------------------------------------------------|
| GET    | `/api/status`              | Port, uptime, loaded modules and stubs, all routes |
| GET    | `/api/history[?service=X]` | Captured request log                               |
| POST   | `/api/reset[?service=X]`   | Clear state (and history on full reset)            |
| GET    | `/api/openapi.json`        | OpenAPI 3.0 spec auto-generated from routes        |

## AWS mock protocols

All on the mock port (default 4566):

```bash
# SQS — JSON/X-Amz-Target
curl -X POST http://localhost:4566 \
  -H "X-Amz-Target: AmazonSQS.CreateQueue" \
  -H "Content-Type: application/x-amz-json-1.0" \
  -d '{"QueueName":"my-queue"}'

# Secrets Manager — JSON/X-Amz-Target
curl -X POST http://localhost:4566 \
  -H "X-Amz-Target: secretsmanager.CreateSecret" \
  -H "Content-Type: application/x-amz-json-1.1" \
  -d '{"Name":"my-secret","SecretString":"val"}'

# SNS — XML/form-URL
curl -X POST http://localhost:4566 \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "Action=CreateTopic&Name=my-topic&Version=2010-03-31"
```

## Gotchas

- **`EXTRA_ARGS[@]` unbound with `set -u`**: empty bash arrays trigger `nounset` in some shells. The script uses
  `${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}` — the standard pattern to expand an empty array safely.
- **State persists across runs in `.cloudmock/`**: the server writes persistent state to `.cloudmock/` in the working
  directory. Call `POST /api/reset` between test runs, or delete the directory.
- **Startup log is invisible**: Jetty INFO lines go to stdout, captured in `/tmp/cloudmock-smoke.log`. If the server
  hangs at startup, that file shows the stack trace.
- **Port conflicts**: default test ports are 14566/14567 to avoid clashing with a running local instance on 4566/4567.
  Change with `--port` / `--api-port`.

## Troubleshooting

| Symptom                           | Fix                                                                  |
|-----------------------------------|----------------------------------------------------------------------|
| `JAR not found`                   | Run `./gradlew :cloudmock-standalone:shadowJar` or use `--build`     |
| `Server did not start within 10s` | Check `/tmp/cloudmock-smoke.log` for the cause                       |
| `Address already in use`          | Another process holds the port — kill it or use `--port=N`           |
| `Unknown module(s): ...`          | Module name typo; valid IDs are `sqs`, `sns`, `secretsmanager`, `s3` |
