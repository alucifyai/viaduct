package viaduct.graphql.schema.scopes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class ResourceFileSchema(
    val declaredSchemaScopes: Set<String>,
    val declaredScopedSchemas: Map<String, Set<String>>,
    val version: String,
) {
    companion object {
        const val FULL_SCHEMA_ID = "FULL"
        const val CURRENT_VERSION = "1"

        fun create(
            declaredSchemaScopes: Set<String> = emptySet(),
            declaredScopedSchemas: Map<String, Set<String>> = emptyMap(),
            version: String = CURRENT_VERSION,
        ): ResourceFileSchema {
            val normalizedScopes = declaredSchemaScopes.toSortedSet()
            val mutableScopedSchemas = declaredScopedSchemas
                .mapValues { it.value.toSortedSet() as Set<String> }
                .toMutableMap()
            if (!mutableScopedSchemas.containsKey(FULL_SCHEMA_ID)) {
                mutableScopedSchemas[FULL_SCHEMA_ID] = emptySet()
            }
            return ResourceFileSchema(
                declaredSchemaScopes = normalizedScopes,
                declaredScopedSchemas = mutableScopedSchemas.toSortedMap(),
                version = version,
            )
        }

        fun objectMapper(): ObjectMapper = jacksonObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
    }
}
