@file:OptIn(InternalApi::class)
@file:Suppress("DEPRECATION", "TYPEALIAS_EXPANSION_DEPRECATION")

package viaduct.service.runtime

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.UnExecutableSchemaGenerator
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import viaduct.apiannotations.InternalApi
import viaduct.engine.SchemaFactory
import viaduct.engine.api.ViaductSchema
import viaduct.service.api.SchemaId

/**
 * Tests for EngineRegistry eager materialization, logging, and fail-fast behavior.
 * Covers TS-036, TS-037, TS-038, TS-039, TS-040, TS-041, ERROR, WARN, TIMING specs.
 */
class EngineRegistryEagerMaterializationTest {

    companion object {
        private const val SIMPLE_SDL = """
            type Query {
                hello: String
            }
        """

        fun createSchemaFromSdl(sdl: String = SIMPLE_SDL): ViaductSchema {
            val graphQLSchema = UnExecutableSchemaGenerator.makeUnExecutableSchema(
                SchemaParser().parse(sdl)
            )
            return ViaductSchema(schema = graphQLSchema)
        }

        fun createSchemaFactory(): SchemaFactory {
            val schemaFactory = mockk<SchemaFactory>()
            every { schemaFactory.fromSdl(any()) } answers { createSchemaFromSdl(firstArg()) }
            every { schemaFactory.fromResources(any(), any()) } answers { createSchemaFromSdl() }
            return schemaFactory
        }

        fun createDocumentProviderFactory() = mockk<DocumentProviderFactory>(relaxed = true)
    }

    // TS-036: schema transformation uses existing ScopedSchemaConfig.Derived (ScopedSchemaBuilder.applyScopes API)
    @Test
    fun `TS-036 scoped schemas use ScopedSchemaConfig Derived which calls ScopedSchemaBuilder applyScopes`() {
        val scopeConfigs = setOf(
            SchemaConfiguration.ScopeConfig("api", setOf("public")),
            SchemaConfiguration.ScopeConfig("adminApi", setOf("admin"))
        )
        val config = SchemaConfiguration.fromSdl(SIMPLE_SDL, scopes = scopeConfigs)

        // Verify all scoped schema configs are the existing Derived implementation
        // (no new transformer class was introduced, per TS-038)
        config.scopedSchemas.values.forEach { scopeConfig ->
            assertInstanceOf(
                SchemaConfiguration.ScopedSchemaConfig.Derived::class.java,
                scopeConfig,
                "Only ScopedSchemaConfig.Derived may be used — no new transformer class"
            )
        }

        // Verify registry builds successfully using the ScopedSchemaBuilder path
        val factory = EngineRegistry.Factory(createSchemaFactory(), createDocumentProviderFactory())
        val registry = factory.create(config)
        assertNotNull(registry.getSchema(SchemaId.Scoped("api", config.resolveSchemaId("api"))))
        assertNotNull(registry.getSchema(SchemaId.Scoped("adminApi", config.resolveSchemaId("adminApi"))))
    }

    // TS-037: returned schemas are graphql-java GraphQLSchema instances
    @Test
    fun `TS-037 returned schemas are graphql-java GraphQLSchema instances`() {
        val config = SchemaConfiguration.fromSdl(SIMPLE_SDL)
        val factory = EngineRegistry.Factory(createSchemaFactory(), createDocumentProviderFactory())
        val registry = factory.create(config)

        val fullSchema = registry.getSchema(SchemaId.Full)
        assertInstanceOf(graphql.schema.GraphQLSchema::class.java, fullSchema.schema)
    }

    // TS-038: no new ScopedSchemaConfig implementation introduced; only Derived is used
    @Test
    fun `TS-038 only existing ScopedSchemaConfig Derived implementation is used`() {
        val scopes = setOf(
            SchemaConfiguration.ScopeConfig("a", setOf("x")),
            SchemaConfiguration.ScopeConfig("b", setOf("y"))
        )
        val config = SchemaConfiguration.fromSdl(SIMPLE_SDL, scopes = scopes)

        config.scopedSchemas.values.forEach { scopeConfig ->
            val className = scopeConfig::class.qualifiedName ?: ""
            assertTrue(
                className.endsWith("Derived"),
                "Expected ScopedSchemaConfig.Derived but found $className"
            )
        }
    }

    // TS-039: getRegisteredSchemaIds returns correct set including FULL implicitly
    @Test
    fun `TS-039 getRegisteredSchemaIds returns correct set including FULL`() {
        val config = SchemaConfiguration.fromResources(setOf("api", "adminApi"))
        val factory = EngineRegistry.Factory(createSchemaFactory(), createDocumentProviderFactory())
        val registry = factory.create(config)

        val ids = registry.getRegisteredSchemaIds()
        assertEquals(3, ids.size, "Expected FULL + api + adminApi = 3 schemas")
        assertTrue(ids.contains(SchemaId.Full), "FULL must always be registered")
        assertTrue(ids.any { it is SchemaId.Scoped && it.id == "api" })
        assertTrue(ids.any { it is SchemaId.Scoped && it.id == "adminApi" })
    }

    // TS-040: EngineRegistry immutable after construction; concurrent reads safe
    @Test
    fun `TS-040 concurrent reads return consistent results with no exceptions`() {
        val config = SchemaConfiguration.fromResources(setOf("api", "adminApi"))
        val factory = EngineRegistry.Factory(createSchemaFactory(), createDocumentProviderFactory())
        val registry = factory.create(config)

        val targetSchemaId = registry.getRegisteredSchemaIds()
            .filterIsInstance<SchemaId.Scoped>()
            .first { it.id == "api" }

        val latch = CountDownLatch(1)
        val results = CopyOnWriteArrayList<ViaductSchema>()
        val errors = CopyOnWriteArrayList<Throwable>()

        val threads = (1..30).map {
            Thread {
                try {
                    latch.await()
                    results.add(registry.getSchema(targetSchemaId))
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }.apply { start() }
        }
        latch.countDown()
        threads.forEach { it.join() }

        assertTrue(errors.isEmpty(), "No concurrent modification exceptions: $errors")
        assertEquals(30, results.size)
        val first = results.first()
        results.forEach { assertSame(first, it, "All reads must return the same cached schema instance") }
    }

    // TS-041: schemas are materialized during EngineRegistry construction, before first request
    @Test
    fun `TS-041 full schema is materialized during create not deferred to first getSchema call`() {
        var buildCallCount = 0
        val schemaFactory = mockk<SchemaFactory>()
        every { schemaFactory.fromSdl(any()) } answers {
            buildCallCount++
            createSchemaFromSdl(firstArg())
        }
        every { schemaFactory.fromResources(any(), any()) } answers {
            buildCallCount++
            createSchemaFromSdl()
        }

        val config = SchemaConfiguration.fromSdl(SIMPLE_SDL)
        val factory = EngineRegistry.Factory(schemaFactory, createDocumentProviderFactory())

        assertEquals(0, buildCallCount, "No schema should be built before create()")
        factory.create(config)
        assertTrue(buildCallCount > 0, "Full schema must be built eagerly during create()")
    }

    // ERROR: materialization failure aborts startup with ViaductSchemaLoadException
    @Test
    fun `ERROR materialization failure aborts startup with ViaductSchemaLoadException`() {
        val schemaFactory = createSchemaFactory()
        val documentProviderFactory = createDocumentProviderFactory()
        val factory = EngineRegistry.Factory(schemaFactory, documentProviderFactory)

        val config = SchemaConfiguration.fromSdl(SIMPLE_SDL)
        val badSchemaId = SchemaId.Scoped("bad-schema", setOf("bad"))

        config.registerSchema(badSchemaId, {
            throw RuntimeException("intentional schema build failure for testing")
        }, lazy = false)

        assertThrows<ViaductSchemaLoadException> {
            factory.create(config)
        }
    }

    // WARN: registering 21+ unique scope sets logs a WARN message
    @Test
    fun `WARN registering 21 scoped schemas logs a WARN message`() {
        val logger = LoggerFactory.getLogger(EngineRegistry::class.java) as Logger
        val listAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(listAppender)
        try {
            val scopes = (1..21).map {
                SchemaConfiguration.ScopeConfig("scope$it", setOf("scope$it"))
            }.toSet()
            val config = SchemaConfiguration.fromSdl(SIMPLE_SDL, scopes = scopes)
            val factory = EngineRegistry.Factory(createSchemaFactory(), createDocumentProviderFactory())
            factory.create(config)

            val warnMessages = listAppender.list.filter { it.level == Level.WARN }
            assertTrue(
                warnMessages.isNotEmpty(),
                "Expected at least one WARN log when >20 scoped schemas are registered"
            )
            assertTrue(
                warnMessages.any { it.formattedMessage.contains("21") },
                "WARN message should mention the count of scoped schemas"
            )
        } finally {
            logger.detachAppender(listAppender)
        }
    }

    // TIMING: DEBUG log line per scope-set materialization contains materialization_time_ms
    @Test
    fun `TIMING DEBUG log contains materialization_time_ms for each eagerly built scope`() {
        val logger = LoggerFactory.getLogger(EngineRegistry::class.java) as Logger
        val originalLevel = logger.level
        logger.level = Level.DEBUG
        val listAppender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(listAppender)
        try {
            val scopes = setOf(
                SchemaConfiguration.ScopeConfig("api", setOf("public")),
                SchemaConfiguration.ScopeConfig("adminApi", setOf("admin"))
            )
            val config = SchemaConfiguration.fromSdl(SIMPLE_SDL, scopes = scopes)
            val factory = EngineRegistry.Factory(createSchemaFactory(), createDocumentProviderFactory())
            factory.create(config)

            val debugMessages = listAppender.list
                .filter { it.level == Level.DEBUG }
                .map { it.formattedMessage }
            val timingMessages = debugMessages.filter { it.contains("materialization_time_ms") }
            assertEquals(
                2,
                timingMessages.size,
                "Expected one DEBUG timing log per eagerly built scoped schema, got: $debugMessages"
            )
        } finally {
            logger.detachAppender(listAppender)
            logger.level = originalLevel
        }
    }

    // EAGER: non-lazy scope schemas are built during create() not on getSchema()
    @Test
    fun `EAGER non-lazy scoped schemas are initialized during registry creation`() {
        var scopeBuildCount = 0
        val config = SchemaConfiguration.fromSdl(SIMPLE_SDL)
        val schemaId = SchemaId.Scoped("counted", setOf("x"))
        config.registerSchema(schemaId, {
            scopeBuildCount++
            createSchemaFromSdl()
        }, lazy = false)

        assertFalse(scopeBuildCount > 0, "Scope schema should not be built before create()")
        val factory = EngineRegistry.Factory(createSchemaFactory(), createDocumentProviderFactory())
        factory.create(config)
        assertEquals(1, scopeBuildCount, "Non-lazy scope schema must be built once during create()")
    }
}
