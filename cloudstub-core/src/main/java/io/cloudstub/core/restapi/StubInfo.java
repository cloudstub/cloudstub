package io.cloudstub.core.restapi;

/** Describes a single registered stub: its protocol and match key. */
public record StubInfo(String protocol, String matchKey) {}
