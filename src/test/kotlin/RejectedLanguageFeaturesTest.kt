import com.h0tk3y.kotlin.staticObjectNotation.*
import com.h0tk3y.kotlin.staticObjectNotation.ElementOrFailureResult.UnsupportedConstruct
import com.h0tk3y.kotlin.staticObjectNotation.LanguageModelUnsupportedConstruct.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertTrue

class RejectedLanguageFeaturesTest {

    @Test
    fun `rejects explicit package declaration`() {
        fun rejects(@Language("kts") code: String) = assertRejectedAsUnsupported<PackageHeader>(code)

        rejects("package com.example")
    }

    @Test
    fun `rejects star imports`() {
        fun rejects(@Language("kts") code: String) = assertRejectedAsUnsupported<StarImport>(code)

        assertAll(
            { rejects("import a.b.c.*") },
            { rejects("import a.*") },
            { rejects("import a.A.Companion.*") },
        )
    }
    
    @Test
    fun `rejects renaming imports`() {
        fun rejects(@Language("kts") code: String) = assertRejectedAsUnsupported<RenamingImport>(code)
        
        assertAll(
            { rejects("import a as b") },
            { rejects("import a.A as B") },
        )
    }

    @Test
    fun `rejects fn call in access chain`() {
        fun rejects(@Language("kts") code: String) = assertRejectedAsUnsupported<FunctionCallInAccessChain>(code)
        assertAll(
            { rejects("a.b.c().d") },
            { rejects("a.b.c().d = e") },
            { rejects("a = b.c().d.e") },
            { rejects("a.b.c().d { }") },
        )
    }

    @Test
    fun `rejects indexed assignments`() {
        fun rejects(@Language("kts") code: String) = assertRejectedAsUnsupported<IndexedAssignment>(code)
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
        fun rejects(@Language("kts") code: String) = assertRejectedAsUnsupported<ExplicitVariableType>(code)
        assertAll(
            { rejects("val a: Int = 0") },
        )
    }

    @Test
    fun `rejects mutable vars`() {
        fun rejects(@Language("kts") code: String) = assertRejectedAsUnsupported<VarUnsupported>(code)
        assertAll(
            { rejects("var x = 0") },
            { rejects("var x: Int = 0") },
            { rejects("var x: Int") },
            { rejects("lateinit var x: Int") },
        )
    }

    @Test
    fun `rejects modifiers on vals`() {
        fun rejects(@Language("kts") code: String) = assertRejectedAsUnsupported<ValModifierUnsupported>(code)
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

    private inline fun <reified T : LanguageModelUnsupportedConstruct> assertRejectedAsUnsupported(code: String) {
        assertTrue { parse(code).single().isUnsupported<T>() }
    }

    private inline fun <reified T : LanguageModelUnsupportedConstruct> ElementOrFailureResult<*>.isUnsupported(): Boolean {
        return this is UnsupportedConstruct && this.failureResult is T
    }
}