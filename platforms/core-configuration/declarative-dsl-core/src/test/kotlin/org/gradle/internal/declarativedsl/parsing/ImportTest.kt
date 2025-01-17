package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.analysis.AnalysisContext
import org.gradle.internal.declarativedsl.analysis.DefaultAnalysisSchema
import org.gradle.internal.declarativedsl.analysis.DefaultDataClass
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.analysis.DefaultOperationGenerationId
import org.gradle.internal.declarativedsl.analysis.ErrorCollectorImpl
import org.gradle.internal.declarativedsl.analysis.ErrorReason
import org.gradle.internal.declarativedsl.analysis.ResolutionError
import org.gradle.internal.declarativedsl.analysis.defaultCodeResolver
import org.gradle.internal.declarativedsl.language.AccessChain
import org.gradle.internal.declarativedsl.language.Import
import org.gradle.internal.declarativedsl.language.SourceIdentifier
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test


class ImportTest {
    private
    val resolver = defaultCodeResolver()
    private
    val errorCollector = ErrorCollectorImpl()

    private
    fun testContext(): AnalysisContext {
        return AnalysisContext(
            DefaultAnalysisSchema(
                DefaultDataClass(DefaultFqName("", ""), "", emptyList(), emptySet(), emptyList(), emptyList(), emptyList()),
                emptyMap(),
                emptyMap(),
                emptyMap(),
                emptySet()
            ),
            emptyMap(),
            errorCollector,
            DefaultOperationGenerationId.finalEvaluation
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
                "C" to DefaultFqName("a.b", "C"),
                "d" to DefaultFqName("a.b.c", "d")
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
                "C" to DefaultFqName("a.b", "C"),
                "D" to DefaultFqName("a.b", "D"),
            ),
            result
        )
        assertEquals(
            listOf(ResolutionError(imports[1], ErrorReason.AmbiguousImport(DefaultFqName("a.c", "C")))),
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
                "C" to DefaultFqName("a.b", "C"),
            ),
            result
        )
        assertTrue(analysisContext.errorCollector.errors.isEmpty())
    }

    private
    fun importOf(vararg nameParts: String) = Import(AccessChain(listOf(*nameParts)), mockSourceData)

    private
    val mockNode = object : LighterASTNode {
        override fun getTokenType(): IElementType = KtNodeTypes.FUN // whatever, doesn't matter

        override fun getStartOffset(): Int = 0

        override fun getEndOffset(): Int = 0
    }

    private
    val sourceIdentifier = SourceIdentifier("test")

    private
    val mockSourceData = mockNode.sourceData(sourceIdentifier, "", 0)
}
