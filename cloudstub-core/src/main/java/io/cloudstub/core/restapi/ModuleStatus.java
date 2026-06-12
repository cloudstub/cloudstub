package io.cloudstub.core.restapi;

import java.util.List;

/** Status snapshot for one loaded service module and its registered stubs. */
public record ModuleStatus(String id, List<StubInfo> stubs) {}
