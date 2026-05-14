package viaduct.gradle.task

import graphql.schema.idl.SchemaParser
import graphql.schema.idl.UnExecutableSchemaGenerator
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.Test
import viaduct.graphql.scopes.errors.SchemaScopeValidationError

/**
 * Unit tests for ScopeMaterializationPipeline.
 *
 * Covers: TS-005, TS-006, TS-007, TS-008, R-22-001, R-22-002.
 */
class ScopeMaterializationPipelineTest {
    private val FIXTURE_SDL = """
        schema { query: Query }

        directive @scope(to: [String!]!) repeatable on OBJECT | INPUT_OBJECT | ENUM | INTERFACE | UNION | SCALAR

        type Query @scope(to: ["*"]) {
            publicField: PublicType
            internalField: InternalType
            bothField: BothType
        }

        type PublicType @scope(to: ["public"]) {
            id: ID!
            name: String!
        }

        type InternalType @scope(to: ["internal"]) {
            id: ID!
            secret: String!
        }

        type BothType @scope(to: ["public", "internal"]) {
            id: ID!
            value: String!
        }
    """.trimIndent()

    private fun buildFixtureSchema() =
        UnExecutableSchemaGenerator.makeUnExecutableSchema(SchemaParser().parse(FIXTURE_SDL))

    // TS-006 — applyScopes filters types correctly for a single scope
    @Test
    fun `applyScopes produces filtered schema containing only public-scoped types`() {
        val fullSchema = buildFixtureSchema()
        val pipeline = ScopeMaterializationPipeline(fullSchema, sortedSetOf("public", "internal"))

        val publicSchema = pipeline.materialize(setOf("public"))

        assertNotNull(publicSchema.getType("PublicType"))
        assertNotNull(publicSchema.getType("BothType"))
        assertNull(publicSchema.getType("InternalType"))
    }

    // TS-006 — applyScopes filters types correctly for the internal scope
    @Test
    fun `applyScopes produces filtered schema containing only internal-scoped types`() {
        val fullSchema = buildFixtureSchema()
        val pipeline = ScopeMaterializationPipeline(fullSchema, sortedSetOf("public", "internal"))

        val internalSchema = pipeline.materialize(setOf("internal"))

        assertNotNull(internalSchema.getType("InternalType"))
        assertNotNull(internalSchema.getType("BothType"))
        assertNull(internalSchema.getType("PublicType"))
    }

    // TS-007 — same scope set returns the same object (cache hit)
    @Test
    fun `materialize returns same object for same scope set`() {
        val fullSchema = buildFixtureSchema()
        val pipeline = ScopeMaterializationPipeline(fullSchema, sortedSetOf("public", "internal"))

        val first = pipeline.materialize(setOf("public"))
        val second = pipeline.materialize(setOf("public"))

        assertSame(first, second)
    }

    // TS-007 — cache hit means materializationCount does not grow on repeat calls
    @Test
    fun `materializationCount equals number of unique scope sets materialized`() {
        val fullSchema = buildFixtureSchema()
        val pipeline = ScopeMaterializationPipeline(fullSchema, sortedSetOf("public", "internal"))

        pipeline.materialize(setOf("public"))
        pipeline.materialize(setOf("public"))    // second call — should be cache hit
        pipeline.materialize(setOf("internal"))

        assertEquals(2, pipeline.materializationCount)
    }

    // TS-008 — empty scope set returns the full schema unchanged (FULL semantics)
    @Test
    fun `materialize with empty scope set returns original full schema`() {
        val fullSchema = buildFixtureSchema()
        val pipeline = ScopeMaterializationPipeline(fullSchema, sortedSetOf("public", "internal"))

        val result = pipeline.materialize(emptySet())

        assertSame(fullSchema, result)
    }

    // R-22-001 — every unique scope set in scopedSchemas produces a materialized schema
    @Test
    fun `all unique scope sets produce a materialized schema`() {
        val fullSchema = buildFixtureSchema()
        val pipeline = ScopeMaterializationPipeline(fullSchema, sortedSetOf("public", "internal"))

        val uniqueScopeSets = setOf(setOf("public"), setOf("internal"), setOf("public", "internal"))
        val results = uniqueScopeSets.associateWith { pipeline.materialize(it) }

        assertEquals(3, results.size)
        results.values.forEach { assertNotNull(it) }
    }

    // R-22-002 — alias IDs sharing the same scope set reuse a single materialization
    @Test
    fun `alias scope sets share a single materialization`() {
        val fullSchema = buildFixtureSchema()
        val pipeline = ScopeMaterializationPipeline(fullSchema, sortedSetOf("public", "internal"))

        // Two aliases "api" and "mobile" both map to {"public"}
        val scopeSet = setOf("public")
        val schema1 = pipeline.materialize(scopeSet)
        val schema2 = pipeline.materialize(scopeSet)

        assertSame(schema1, schema2)
        assertEquals(1, pipeline.materializationCount)
    }

    // TS-005 — materialize is called per unique scope set, not per alias ID
    @Test
    fun `materializationCount reflects unique scope sets not total alias count`() {
        val fullSchema = buildFixtureSchema()
        val pipeline = ScopeMaterializationPipeline(fullSchema, sortedSetOf("public", "internal"))

        // 3 aliases, 2 unique scope sets
        val scopeSets = listOf(
            setOf("public"),     // alias "api"
            setOf("public"),     // alias "mobile"
            setOf("internal"),   // alias "admin"
        )
        scopeSets.forEach { pipeline.materialize(it) }

        assertEquals(2, pipeline.materializationCount)
    }

    // Scope set ordering should not affect cache key
    @Test
    fun `scope set ordering does not affect cache`() {
        val fullSchema = buildFixtureSchema()
        val pipeline = ScopeMaterializationPipeline(fullSchema, sortedSetOf("public", "internal"))

        val a = pipeline.materialize(setOf("public", "internal"))
        val b = pipeline.materialize(setOf("internal", "public"))

        assertSame(a, b)
        assertEquals(1, pipeline.materializationCount)
    }
}
