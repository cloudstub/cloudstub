#!/usr/bin/env bash
# Resolve the Gradle publish task list from the optional MODULES input.
#
# Usage: publish-tasks.sh <task-name>
#   <task-name> is the publish task, e.g. publishAndReleaseToMavenCentral.
#   Reads MODULES (comma-separated module names) from the environment.
#
# Prints a single "tasks=<gradle args>" line for $GITHUB_OUTPUT:
#   - MODULES blank  -> the aggregate task (publishes every publishable module, the default)
#   - MODULES set    -> one :<module>:<task-name> per listed module (publishes only those)
#
# Publishing a subset is safe: the standalone server resolves an unpublished module version down
# to the highest published version <= the core version, and Maven consumers pin coordinates and a
# minimum core via CloudStub-Core-Min-Version.
set -euo pipefail

task="${1:?task name required}"
modules="${MODULES:-}"

if [[ -z "${modules//[[:space:]]/}" ]]; then
    echo "tasks=${task}"
    exit 0
fi

tasks=""
IFS=',' read -ra entries <<<"$modules"
for entry in "${entries[@]}"; do
    name="${entry//[[:space:]]/}"
    [[ -z "$name" ]] && continue
    if [[ ! "$name" =~ ^cloudstub-[a-z0-9-]+$ ]]; then
        echo "::error::Invalid module name '$name' (expected e.g. cloudstub-core, cloudstub-sqs)." >&2
        exit 1
    fi
    tasks+=" :${name}:${task}"
done

if [[ -z "${tasks// /}" ]]; then
    echo "::error::MODULES was set but resolved to no module." >&2
    exit 1
fi

echo "tasks=${tasks# }"
