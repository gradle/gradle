package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.*
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.UnsupportedLanguageFeature.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertTrue

class RejectedLanguageFeaturesTest {

    @Test
    fun `rejects explicit package declaration`() {
        fun rejects(@Language("kts") code: String) = rejectsAndReportsFeature(PackageHeader, code)

        rejects("package com.example")
    }

    @Test
    fun `rejects star imports`() {
        fun rejects(@Language("kts") code: String) = rejectsAndReportsFeature(StarImport, code)

        assertAll(
            { rejects("import a.b.c.*") },
            { rejects("import a.*") },
            { rejects("import a.A.Companion.*") },
        )
    }
    
    @Test
    fun `rejects renaming imports`() {
        fun rejects(@Language("kts") code: String) = rejectsAndReportsFeature(RenamingImport, code)
        
        assertAll(
            { rejects("import a as b") },
            { rejects("import a.A as B") },
        )
    }

    @Test
    fun `rejects indexed assignments`() {
        fun rejects(@Language("kts") code: String) = rejectsAndReportsFeature(Indexing, code)
        assertAll(
            { rejects("a[b] = 1") },
            { rejects("a.b.c[1] = 1") },
            { rejects("a[b].c = 1") },
            { rejects("a.b.c()[1] = 1") },
            { rejects("a.b[a[b]].c = 1") }
        )
    }

    @Test
    fun `rejects variable type labels`() {
        fun rejects(@Language("kts") code: String) = rejectsAndReportsFeature(ExplicitVariableType, code)
        assertAll(
            { rejects("val a: Int = 0") },
        )
    }

    @Test
    fun `rejects mutable vars`() {
        fun rejects(@Language("kts") code: String) = assertTrue {
            fun isVarReported(failure: FailingResult): Boolean =
                failure is UnsupportedConstruct && failure.languageFeature == LocalVarNotSupported ||
                        failure is MultipleFailuresResult && failure.failures.any { isVarReported(it) }
            isVarReported(parse(code).single() as FailingResult)
        }
        assertAll(
            { rejects("var x = 0") },
            { rejects("var x: Int = 0") },
            { rejects("var x: Int") },
            { rejects("lateinit var x: Int") },
        )
    }

    @Test
    fun `rejects modifiers on vals`() {
        fun rejects(@Language("kts") code: String) = rejectsAndReportsFeature(ValModifierNotSupported, code)
        assertAll(
            { rejects("public val x: Int = 0") },
            { rejects("private val x: Int") }
        )
    }

    @Test
    fun `rejects type declaration`() {
        assertAll(
            { assertTrue { parse("class A").single().isUnsupported<TypeDeclaration>() } },
            { assertTrue { parse("class A { }").single().isUnsupported<TypeDeclaration>() } },
            { assertTrue { parse("interface A { }").single().isUnsupported<TypeDeclaration>() } },
            { assertTrue { parse("sealed interface A { }").single().isUnsupported<TypeDeclaration>() } },
            { assertTrue { parse("typealias A = Unit").single().isUnsupported<TypeDeclaration>() } },
        )
    }

    private fun isFeatureReported(feature: UnsupportedLanguageFeature, failure: FailingResult): Boolean =
        failure is UnsupportedConstruct && failure.languageFeature == feature ||
                failure is MultipleFailuresResult && failure.failures.any { isFeatureReported(feature, it) }

    private fun rejectsAndReportsFeature(feature: UnsupportedLanguageFeature, code: String) {
        assertTrue {
            val result = parse(code).single()
            result is FailingResult && isFeatureReported(feature, result)
        }
    }

    private inline fun <reified T : UnsupportedLanguageFeature> ElementResult<*>.isUnsupported(): Boolean {
        return this is UnsupportedConstruct && this.languageFeature is T
    }
}