package viaduct.graphql.schema.scopes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScopeIdSyntaxTest {

    @Test
    fun `PATTERN_STRING equals the expected regex`() {
        assertEquals("^[a-z]+$", ScopeIdSyntax.PATTERN_STRING)
    }

    // TS-044: valid ids succeed
    @Test
    fun `valid id public succeeds`() {
        val result = ScopeIdSyntax.validate("public")
        assertTrue(result.isSuccess)
        assertEquals("public", result.getOrNull())
    }

    @Test
    fun `valid id internal succeeds`() {
        val result = ScopeIdSyntax.validate("internal")
        assertTrue(result.isSuccess)
        assertEquals("internal", result.getOrNull())
    }

    @Test
    fun `valid id single letter succeeds`() {
        val result = ScopeIdSyntax.validate("a")
        assertTrue(result.isSuccess)
        assertEquals("a", result.getOrNull())
    }

    @Test
    fun `valid id full alphabet succeeds`() {
        val result = ScopeIdSyntax.validate("abcdefghijklmnopqrstuvwxyz")
        assertTrue(result.isSuccess)
        assertEquals("abcdefghijklmnopqrstuvwxyz", result.getOrNull())
    }

    // TS-053: invalid ids fail with actionable message
    @Test
    fun `invalid id uppercase fails`() {
        val result = ScopeIdSyntax.validate("Public")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("\"Public\""))
        assertTrue(ex.message!!.contains(ScopeIdSyntax.PATTERN_STRING))
    }

    @Test
    fun `invalid id digits only fails`() {
        val result = ScopeIdSyntax.validate("123")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("\"123\""))
        assertTrue(ex.message!!.contains(ScopeIdSyntax.PATTERN_STRING))
    }

    @Test
    fun `invalid id empty string fails`() {
        val result = ScopeIdSyntax.validate("")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("\"\""))
        assertTrue(ex.message!!.contains(ScopeIdSyntax.PATTERN_STRING))
    }

    @Test
    fun `invalid id with hyphen fails`() {
        val result = ScopeIdSyntax.validate("a-b")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("\"a-b\""))
        assertTrue(ex.message!!.contains(ScopeIdSyntax.PATTERN_STRING))
    }

    @Test
    fun `invalid id with underscore fails`() {
        val result = ScopeIdSyntax.validate("a_b")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("\"a_b\""))
        assertTrue(ex.message!!.contains(ScopeIdSyntax.PATTERN_STRING))
    }

    @Test
    fun `invalid id with leading space fails`() {
        val result = ScopeIdSyntax.validate(" a")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("\" a\""))
        assertTrue(ex.message!!.contains(ScopeIdSyntax.PATTERN_STRING))
    }

    @Test
    fun `invalid id mixed alphanumeric fails`() {
        val result = ScopeIdSyntax.validate("a1")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue(ex!!.message!!.contains("\"a1\""))
        assertTrue(ex.message!!.contains(ScopeIdSyntax.PATTERN_STRING))
    }

    @Test
    fun `getOrNull returns null for invalid id`() {
        assertNull(ScopeIdSyntax.validate("Bad").getOrNull())
    }
}
