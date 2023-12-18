package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisContext
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ErrorReason
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionError
import com.h0tk3y.kotlin.staticObjectNotation.analysis.*
import com.h0tk3y.kotlin.staticObjectNotation.language.AccessChain
import com.h0tk3y.kotlin.staticObjectNotation.language.Import
import com.h0tk3y.kotlin.staticObjectNotation.language.SourceIdentifier
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.kotlinFunctionAsConfigureLambda
import kotlinx.ast.common.ast.Ast
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportTest {
    private val resolver = defaultCodeResolver()

    private fun testContext(errors: MutableList<ResolutionError>): AnalysisContext {
        return AnalysisContext(
            AnalysisSchema(
                DataType.DataClass(Unit::class, emptyList(), emptyList(), emptyList()),
                emptyMap(),
                emptyMap(),
                emptyMap(),
                emptySet(),
                kotlinFunctionAsConfigureLambda
            ),
            emptyMap(),
            errors::add
        )
    }

    @Test
    fun `collects basic imports`() {
        val imports = listOf(
            importOf("a", "b", "C"),
            importOf("a", "b", "c", "d")
        )
        val errors = mutableListOf<ResolutionError>()
        val result = defaultCodeResolver().collectImports(imports, testContext(errors))

        assertEquals(
            mapOf(
                "C" to FqName("a.b", "C"),
                "d" to FqName("a.b.c", "d")
            ),
            result
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `reports ambiguous import errors`() {
        val imports = listOf(
            importOf("a", "b", "C"),
            importOf("a", "c", "C"),
            importOf("a", "b", "D")
        )
        val errors = mutableListOf<ResolutionError>()
        val result = resolver.collectImports(imports, testContext(errors))

        assertEquals(
            mapOf(
                "C" to FqName("a.b", "C"),
                "D" to FqName("a.b", "D"),
            ),
            result
        )
        assertEquals(
            listOf(ResolutionError(imports[1], ErrorReason.AmbiguousImport(FqName("a.c", "C")))),
            errors
        )
    }

    @Test
    fun `does not report errors on the same import appearing more than once`() {
        val imports = listOf(
            importOf("a", "b", "C"),
            importOf("a", "b", "C"),
            importOf("a", "b", "C")
        )
        val errors = mutableListOf<ResolutionError>()
        val result = resolver.collectImports(imports, testContext(errors))

        assertEquals(
            mapOf(
                "C" to FqName("a.b", "C"),
            ),
            result
        )
        assertTrue(errors.isEmpty())
    }

    private fun importOf(vararg nameParts: String) = Import(AccessChain(listOf(*nameParts)), ast.sourceData(sourceIdentifier))

    private val ast = object : Ast {
        override val description: String = "test"
    }

    private val sourceIdentifier = SourceIdentifier("test")
}
