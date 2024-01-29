package com.h0tk3y.kotlin.staticObjectNotation.parsing

import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisContext
import com.h0tk3y.kotlin.staticObjectNotation.analysis.AnalysisSchema
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataType
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ErrorCollectorImpl
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ErrorReason
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FqName
import com.h0tk3y.kotlin.staticObjectNotation.analysis.ResolutionError
import com.h0tk3y.kotlin.staticObjectNotation.analysis.defaultCodeResolver
import com.h0tk3y.kotlin.staticObjectNotation.language.AccessChain
import com.h0tk3y.kotlin.staticObjectNotation.language.Import
import com.h0tk3y.kotlin.staticObjectNotation.language.SourceIdentifier
import kotlinx.ast.common.ast.Ast
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportTest {
    private val resolver = defaultCodeResolver()
    private val errorCollector = ErrorCollectorImpl()

    private fun testContext(): AnalysisContext {
        return AnalysisContext(
            AnalysisSchema(
                DataType.DataClass(FqName("", ""), emptySet(), emptyList(), emptyList(), emptyList()),
                emptyMap(),
                emptyMap(),
                emptyMap(),
                emptySet()
            ),
            emptyMap(),
            errorCollector
        )
    }

    @Test
    fun `collects basic imports`() {
        val imports = listOf(
            importOf("a", "b", "C"),
            importOf("a", "b", "c", "d")
        )
        val analysisContext = testContext()
        val result = defaultCodeResolver().collectImports(imports, analysisContext)

        assertEquals(
            mapOf(
                "C" to FqName("a.b", "C"),
                "d" to FqName("a.b.c", "d")
            ),
            result
        )
        assertTrue(analysisContext.errorCollector.errors.isEmpty())
    }

    @Test
    fun `reports ambiguous import errors`() {
        val imports = listOf(
            importOf("a", "b", "C"),
            importOf("a", "c", "C"),
            importOf("a", "b", "D")
        )
        val analysisContext = testContext()
        val result = resolver.collectImports(imports, analysisContext)

        assertEquals(
            mapOf(
                "C" to FqName("a.b", "C"),
                "D" to FqName("a.b", "D"),
            ),
            result
        )
        assertEquals(
            listOf(ResolutionError(imports[1], ErrorReason.AmbiguousImport(FqName("a.c", "C")))),
            analysisContext.errorCollector.errors
        )
    }

    @Test
    fun `does not report errors on the same import appearing more than once`() {
        val imports = listOf(
            importOf("a", "b", "C"),
            importOf("a", "b", "C"),
            importOf("a", "b", "C")
        )
        val analysisContext = testContext()
        val result = resolver.collectImports(imports, analysisContext)

        assertEquals(
            mapOf(
                "C" to FqName("a.b", "C"),
            ),
            result
        )
        assertTrue(analysisContext.errorCollector.errors.isEmpty())
    }

    private fun importOf(vararg nameParts: String) = Import(AccessChain(listOf(*nameParts)), ast.sourceData(sourceIdentifier))

    private val ast = object : Ast {
        override val description: String = "test"
    }

    private val sourceIdentifier = SourceIdentifier("test")
}
