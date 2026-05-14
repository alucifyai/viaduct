package viaduct.service.runtime

import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import viaduct.graphql.schema.scopes.ResourceFileSchema

/**
 * Tests for SchemaConfiguration.fromResources(schemaIds) and resolveSchemaId.
 *
 * The test fixture at META-INF/viaduct/schema-scoping.json declares:
 *   declaredSchemaScopes: [admin, internal, public]
 *   declaredScopedSchemas: { FULL: [], adminApi: [admin], api: [internal, public] }
 *   version: 1
 */
class SchemaConfigurationFromResourcesTest {

    // TS-013: fromResources resolves schema IDs correctly from a META-INF fixture
    @Test
    fun `TS-013 fromResources resolves schema IDs from fixture`() {
        val config = SchemaConfiguration.fromResources(setOf("api", "adminApi", "FULL"))

        assertEquals(2, config.scopedSchemas.size)

        val apiKey = config.scopedSchemas.keys.find { it.id == "api" }
        assertNotNull(apiKey)
        assertEquals(setOf("internal", "public"), apiKey.scopeIds)

        val adminKey = config.scopedSchemas.keys.find { it.id == "adminApi" }
        assertNotNull(adminKey)
        assertEquals(setOf("admin"), adminKey.scopeIds)
    }

    // TS-014: resolveSchemaId returns correct scope set for declared id; returns emptySet for FULL
    @Test
    fun `TS-014 resolveSchemaId returns scope set and emptySet for FULL`() {
        val config = SchemaConfiguration.fromResources(setOf("api", "adminApi"))

        assertEquals(setOf("internal", "public"), config.resolveSchemaId("api"))
        assertEquals(setOf("admin"), config.resolveSchemaId("adminApi"))
        assertEquals(emptySet<String>(), config.resolveSchemaId("FULL"))
    }

    // TS-015: FULL schema ID is always resolvable
    @Test
    fun `TS-015 FULL schema ID is always resolvable`() {
        val config = SchemaConfiguration.fromResources(setOf("api"))
        assertEquals(emptySet<String>(), config.resolveSchemaId("FULL"))
    }

    // TS-016: empty scope set resolves to full schema (via the existing applyScopes path)
    @Test
    fun `TS-016 schema with empty scopeIds resolves to full schema via applyScopes`() {
        val tempDir = Files.createTempDirectory("viaduct-ts016")
        try {
            val metaInfDir = tempDir.resolve("META-INF/viaduct")
            Files.createDirectories(metaInfDir)
            val fixture = ResourceFileSchema.create(
                declaredSchemaScopes = setOf("public"),
                declaredScopedSchemas = mapOf("api" to setOf("public"), "emptyScoped" to emptySet())
            )
            Files.writeString(
                metaInfDir.resolve("schema-scoping.json"),
                ResourceFileSchema.objectMapper().writeValueAsString(fixture)
            )
            val classLoader = URLClassLoader(arrayOf(tempDir.toUri().toURL()), ClassLoader.getPlatformClassLoader())
            val config = SchemaConfiguration.fromResources(setOf("emptyScoped"), classLoader)

            val emptyKey = config.scopedSchemas.keys.find { it.id == "emptyScoped" }
            assertNotNull(emptyKey)
            assertTrue(emptyKey.scopeIds.isEmpty(), "Expected empty scopeIds for emptyScoped schema")
            // The ScopedSchemaConfig.Derived.build path returns the full schema when scopeIds is empty
            assertEquals(emptySet<String>(), config.resolveSchemaId("emptyScoped"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    // TS-017: resource file read once, cached immutably
    @Test
    fun `TS-017 resource file is loaded at construction time not on each resolveSchemaId`() {
        val config = SchemaConfiguration.fromResources(setOf("api", "adminApi"))

        // Calling resolveSchemaId many times is consistent and fast (no classpath IO)
        val expected = setOf("internal", "public")
        repeat(100) {
            assertEquals(expected, config.resolveSchemaId("api"))
        }
    }

    // TS-055: SchemaConfiguration immutable after construction; concurrent reads safe
    @Test
    fun `TS-055 concurrent reads are safe`() {
        val config = SchemaConfiguration.fromResources(setOf("api", "adminApi"))
        val latch = CountDownLatch(1)
        val results = CopyOnWriteArrayList<Set<String>>()

        val threads = (1..30).map {
            Thread {
                latch.await()
                results.add(config.resolveSchemaId("api"))
            }.apply { start() }
        }
        latch.countDown()
        threads.forEach { it.join() }

        assertEquals(30, results.size)
        results.forEach { assertEquals(setOf("internal", "public"), it) }
    }

    // ERROR: fromResources throws ViaductInvalidConfigurationException for undeclared schema id
    @Test
    fun `ERROR fromResources throws ViaductInvalidConfigurationException for undeclared schema id`() {
        val ex = assertThrows<ViaductInvalidConfigurationException> {
            SchemaConfiguration.fromResources(setOf("undeclaredId"))
        }
        assertTrue(ex.message!!.contains("undeclaredId"), "Exception message should contain the offending id")
        assertTrue(
            ex.message!!.contains("api") || ex.message!!.contains("adminApi"),
            "Exception message should contain declared ids"
        )
    }

    // ERROR: fromResources throws ViaductSchemaLoadException for missing META-INF file (non-FULL ids)
    @Test
    fun `ERROR fromResources throws ViaductSchemaLoadException for missing META-INF file`() {
        val classLoader = URLClassLoader(emptyArray(), ClassLoader.getPlatformClassLoader())
        assertThrows<ViaductSchemaLoadException> {
            SchemaConfiguration.fromResources(setOf("api"), classLoader)
        }
    }

    // ERROR: fromResources throws ViaductSchemaLoadException for malformed JSON
    @Test
    fun `ERROR fromResources throws ViaductSchemaLoadException for malformed JSON`() {
        val tempDir = Files.createTempDirectory("viaduct-malformed")
        try {
            val metaInfDir = tempDir.resolve("META-INF/viaduct")
            Files.createDirectories(metaInfDir)
            Files.writeString(metaInfDir.resolve("schema-scoping.json"), "{ not valid json !!!")

            val classLoader = URLClassLoader(arrayOf(tempDir.toUri().toURL()), ClassLoader.getPlatformClassLoader())
            assertThrows<ViaductSchemaLoadException> {
                SchemaConfiguration.fromResources(setOf("api"), classLoader)
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    // ROUND-TRIP: build fixture → serialize → fromResources recovers exact scope sets
    @Test
    fun `ROUND-TRIP fromResources recovers exact scope sets from serialized ResourceFileSchema`() {
        val original = ResourceFileSchema.create(
            declaredSchemaScopes = setOf("public", "internal", "admin"),
            declaredScopedSchemas = mapOf(
                "api" to setOf("public", "internal"),
                "adminApi" to setOf("admin")
            )
        )

        val tempDir = Files.createTempDirectory("viaduct-roundtrip")
        try {
            val metaInfDir = tempDir.resolve("META-INF/viaduct")
            Files.createDirectories(metaInfDir)
            val json = ResourceFileSchema.objectMapper().writeValueAsString(original)
            Files.writeString(metaInfDir.resolve("schema-scoping.json"), json)

            val classLoader = URLClassLoader(arrayOf(tempDir.toUri().toURL()), ClassLoader.getPlatformClassLoader())
            val config = SchemaConfiguration.fromResources(setOf("api", "adminApi", "FULL"), classLoader)

            assertEquals(original.declaredScopedSchemas["api"], config.resolveSchemaId("api"))
            assertEquals(original.declaredScopedSchemas["adminApi"], config.resolveSchemaId("adminApi"))
            assertEquals(emptySet<String>(), config.resolveSchemaId("FULL"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    // NoScopesMode: fromResources(setOf("FULL")) succeeds without META-INF file
    @Test
    fun `NoScopesMode fromResources with only FULL succeeds when no META-INF file present`() {
        val classLoader = URLClassLoader(emptyArray(), ClassLoader.getPlatformClassLoader())
        val config = SchemaConfiguration.fromResources(setOf("FULL"), classLoader)

        assertEquals(0, config.scopedSchemas.size)
        assertNotNull(config.fullSchemaConfig)
    }

    // resolveSchemaId throws for undeclared id on instance level
    @Test
    fun `resolveSchemaId throws ViaductInvalidConfigurationException for unregistered id`() {
        val config = SchemaConfiguration.fromResources(setOf("api"))

        val ex = assertThrows<ViaductInvalidConfigurationException> {
            config.resolveSchemaId("notRegistered")
        }
        assertTrue(ex.message!!.contains("notRegistered"))
    }
}
