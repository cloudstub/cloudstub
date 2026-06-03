package io.cloudmock.codegen;

/**
 * A single file produced by the generator: a path relative to the module root and its content.
 */
record GeneratedFile(String relativePath, String content) {}
