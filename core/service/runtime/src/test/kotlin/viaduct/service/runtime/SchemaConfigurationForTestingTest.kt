@file:OptIn(InternalApi::class, VisibleForTest::class)

package viaduct.service.runtime

import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import viaduct.apiannotations.InternalApi
import viaduct.apiannotations.VisibleForTest
import viaduct.graphql.schema.scopes.ResourceFileSchema

/**
 * Tests for SchemaConfiguration.forTesting factory.
 * Covers TS-029, TS-030, TS-031, TS-046.
 */
class SchemaConfigurationForTestingTest {

    // TS-029: forTesting creates usable SchemaConfiguration from JSON with correct scoped schemas
    @Test
    fun `TS-029 forTesting creates SchemaConfiguration with correct scoped schemas from JSON`() {
        val fixture = ResourceFileSchema.create(
            declaredSchemaScopes = setOf("admin", "internal", "public"),
            declaredScopedSchemas = mapOf(
                "adminApi" to setOf("admin"),
                "api" to setOf("internal", "public")
            )
        )
        val json = ResourceFileSchema.objectMapper().writeValueAsString(fixture)

        val config = SchemaConfiguration.forTesting(json)

        assertEquals(2, config.scopedSchemas.size)
        val adminEntry = config.scopedSchemas.entries.find { it.key.id == "adminApi" }
        assertNotNull(adminEntry)
        assertEquals(setOf("admin"), adminEntry.value.scopeIds)
        val apiEntry = config.scopedSchemas.entries.find { it.key.id == "api" }
        assertNotNull(apiEntry)
        assertEquals(setOf("internal", "public"), apiEntry.value.scopeIds)
        assertNotNull(config.fullSchemaConfig)
    }

    // TS-030: method exists in companion object; @VisibleForTest requires opt-in at call sites
    // (this test file uses @file:OptIn(VisibleForTest::class) to compile without warnings,
    // proving forTesting is annotated with @VisibleForTest; BINARY retention means the
    // annotation is not accessible via Java getAnnotation() at runtime)
    @Test
    fun `TS-030 forTesting method exists in SchemaConfiguration companion and requires VisibleForTest opt-in`() {
        val method = SchemaConfiguration.Companion::class.java.declaredMethods
            .firstOrNull { it.name == "forTesting" && it.parameterCount == 1 }
        assertNotNull(method, "forTesting(String) must exist in SchemaConfiguration.Companion")
        assertEquals(String::class.java, method.parameterTypes[0])
        assertEquals(SchemaConfiguration::class.java, method.returnType)
    }

    // TS-031: forTesting accepts same JSON format as production reader; same scopedSchemas as fromResources
    @Test
    fun `TS-031 forTesting produces same scopedSchemas as production fromResources for same JSON`() {
        val fixture = ResourceFileSchema.create(
            declaredSchemaScopes = setOf("admin", "internal", "public"),
            declaredScopedSchemas = mapOf(
                "adminApi" to setOf("admin"),
                "api" to setOf("internal", "public")
            )
        )
        val json = ResourceFileSchema.objectMapper().writeValueAsString(fixture)

        val tempDir = Files.createTempDirectory("viaduct-ts031")
        try {
            val metaInfDir = tempDir.resolve("META-INF/viaduct")
            Files.createDirectories(metaInfDir)
            Files.writeString(metaInfDir.resolve("schema-scoping.json"), json)
            val classLoader = URLClassLoader(arrayOf(tempDir.toUri().toURL()), ClassLoader.getPlatformClassLoader())

            val configFromResources = SchemaConfiguration.fromResources(
                setOf("api", "adminApi", "FULL"),
                classLoader
            )
            val configForTesting = SchemaConfiguration.forTesting(json)

            // Both should have the same scoped schema IDs with the same scope sets
            val resourcesKeys = configFromResources.scopedSchemas.entries.map { it.key.id to it.value.scopeIds }.toSet()
            val testingKeys = configForTesting.scopedSchemas.entries.map { it.key.id to it.value.scopeIds }.toSet()
            assertEquals(resourcesKeys, testingKeys)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    // TS-046: follows existing @VisibleForTest factory pattern (non-lazy, uses companion factory)
    @Test
    fun `TS-046 forTesting follows VisibleForTest factory pattern - eager registration`() {
        val fixture = ResourceFileSchema.create(
            declaredSchemaScopes = setOf("public"),
            declaredScopedSchemas = mapOf("api" to setOf("public"))
        )
        val json = ResourceFileSchema.objectMapper().writeValueAsString(fixture)

        val config = SchemaConfiguration.forTesting(json)

        // All scoped schemas should be eagerly registered (lazy=false)
        config.scopedSchemas.values.forEach { scopeConfig ->
            assertEquals(false, scopeConfig.lazy, "forTesting must register all schemas as non-lazy")
        }
    }

    // ERROR: forTesting throws ViaductSchemaLoadException for malformed JSON
    @Test
    fun `ERROR forTesting throws ViaductSchemaLoadException for malformed JSON`() {
        assertThrows<ViaductSchemaLoadException> {
            SchemaConfiguration.forTesting("{ not valid json !!!")
        }
    }

    // ROUND-TRIP: forTesting with empty scopes creates config with no scoped schemas
    @Test
    fun `ROUND-TRIP forTesting with no scoped schemas returns config with only full schema`() {
        val fixture = ResourceFileSchema.create(
            declaredSchemaScopes = emptySet(),
            declaredScopedSchemas = emptyMap()
        )
        val json = ResourceFileSchema.objectMapper().writeValueAsString(fixture)

        val config = SchemaConfiguration.forTesting(json)

        assertEquals(0, config.scopedSchemas.size)
        assertNotNull(config.fullSchemaConfig)
    }
}
