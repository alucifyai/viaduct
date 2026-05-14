package viaduct.service.api

import viaduct.apiannotations.StableApi

/**
 * Identifies which schema variant to use when executing a GraphQL operation.
 *
 * Viaduct supports multiple schema variants for a single service:
 * - [Full] — the default, complete schema containing all types and fields.
 * - A named scoped variant — a subset of the full schema restricted by scope IDs declared via
 *   [viaduct.service.runtime.SchemaConfiguration]. Construct with `SchemaId("myScope")`.
 * - [None] — represents a non-existent schema, used as a sentinel value.
 *
 * @see viaduct.service.ViaductBuilder.withSchemaConfiguration
 */
@StableApi
open class SchemaId(
    open val id: String
) {
    /**
     * A schema ID that represents a full schema without any scoping.
     */
    @StableApi
    object Full : SchemaId("FULL")

    /**
     * Represents a non-existent schema.
     */
    @StableApi
    object None : SchemaId("NONE")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SchemaId) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "SchemaId(id='$id')"
}
