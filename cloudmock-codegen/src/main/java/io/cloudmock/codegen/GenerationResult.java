package io.cloudmock.codegen;

import java.util.List;

/** The outcome of a single model generation run. */
record GenerationResult(String serviceId, String moduleName, List<GeneratedFile> files) {}
