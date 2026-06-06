package io.cloudmock.core.spi.restapi;

/**
 * Describes a single parameter of an API route.
 *
 * <p>Carried in {@code /api/status} so a generic client (e.g. the CLI) can render the
 * parameter as a command-line option without any compile-time knowledge of the module.
 * Parameters are passed to the route as query-string values.
 *
 * @param name        parameter name, also the query-string key and CLI option name
 * @param required    whether the parameter must be supplied
 * @param description one-line help text
 */
public record ApiParam(String name, boolean required, String description) {}
