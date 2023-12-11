package io.tolgee.dtos.request.export

enum class ExportFormat(val extension: String, val mediaType: String) {
  JAVA_PROPERTIES("properties", "text/x-java-properties"),
  JSON("json", "application/json"),
  XLIFF("xlf", "application/x-xliff+xml"),
}
