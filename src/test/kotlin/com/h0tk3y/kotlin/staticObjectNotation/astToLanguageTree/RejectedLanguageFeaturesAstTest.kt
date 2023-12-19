package com.example.com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.AbstractRejectedLanguageFeaturesTest
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.ElementResult
import com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree.ParseTestUtil
import org.junit.jupiter.api.Test

class RejectedLanguageFeaturesAstTest: AbstractRejectedLanguageFeaturesTest() {

    override fun parse(code: String): List<ElementResult<*>> = ParseTestUtil.parseWithAst(code)

    @Test
    fun `rejects explicit package declaration`() {
        val code = "package com.example"
        val expected =
            "UnsupportedConstruct(languageFeature = PackageHeader, potentialElementSource = indexes: 0..19, file: test, erroneousSource = indexes: 0..19, file: test)"
        assertResult(expected, code)
    }

    @Test
    fun `rejects star imports`() {
        val code = """
            import a.b.c.*
            import a.*
            import a.A.Companion.*
            """.trimIndent()
        val expected = """
            UnsupportedConstruct(languageFeature = StarImport, potentialElementSource = indexes: 0..15, file: test, erroneousSource = indexes: 13..14, file: test)
            UnsupportedConstruct(languageFeature = StarImport, potentialElementSource = indexes: 15..26, file: test, erroneousSource = indexes: 24..25, file: test)
            UnsupportedConstruct(languageFeature = StarImport, potentialElementSource = indexes: 26..48, file: test, erroneousSource = indexes: 47..48, file: test)
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `rejects renaming imports`() {
        val code = """
            import a as b
            import a.A as B
            """.trimIndent()
        val expected = """
            UnsupportedConstruct(languageFeature = RenamingImport, potentialElementSource = indexes: 0..14, file: test, erroneousSource = indexes: 9..13, file: test)
            UnsupportedConstruct(languageFeature = RenamingImport, potentialElementSource = indexes: 14..29, file: test, erroneousSource = indexes: 25..29, file: test)
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `rejects type declaration`() {
        val code = """
        class A
        class A { }
        interface A { }
        sealed interface A { }
        typealias A = Unit
        """.trimIndent()
        val expected =
            """
            UnsupportedConstruct(languageFeature = TypeDeclaration, potentialElementSource = indexes: 0..7, file: test, erroneousSource = indexes: 0..7, file: test)
            UnsupportedConstruct(languageFeature = TypeDeclaration, potentialElementSource = indexes: 8..19, file: test, erroneousSource = indexes: 8..19, file: test)
            UnsupportedConstruct(languageFeature = TypeDeclaration, potentialElementSource = indexes: 20..35, file: test, erroneousSource = indexes: 20..35, file: test)
            UnsupportedConstruct(languageFeature = TypeDeclaration, potentialElementSource = indexes: 36..58, file: test, erroneousSource = indexes: 36..58, file: test)
            UnsupportedConstruct(languageFeature = TypeDeclaration, potentialElementSource = indexes: 59..77, file: test, erroneousSource = indexes: 59..77, file: test)
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `rejects variable type labels`() {
        val code = """
            val a: Int = 0
            """.trimIndent()
        val expected = """
            UnsupportedConstruct(languageFeature = ExplicitVariableType, potentialElementSource = indexes: 4..10, file: test, erroneousSource = indexes: 7..10, file: test)
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `rejects indexed assignments`() {
        val code = """
            a[b] = 1
            a.b.c[1] = 1
            a[b].c = 1
            a.b.c()[1] = 1
            a.b[a[b]].c = 1
            a = b[1]
            a = b[1][2]
            a = b[b[1]]
            """.trimIndent()
        val expected = """
            UnsupportedConstruct(languageFeature = Indexing, potentialElementSource = indexes: 1..4, file: test, erroneousSource = indexes: 1..4, file: test)
            UnsupportedConstruct(languageFeature = Indexing, potentialElementSource = indexes: 14..17, file: test, erroneousSource = indexes: 14..17, file: test)
            UnsupportedConstruct(languageFeature = Indexing, potentialElementSource = indexes: 23..26, file: test, erroneousSource = indexes: 23..26, file: test)
            UnsupportedConstruct(languageFeature = Indexing, potentialElementSource = indexes: 40..43, file: test, erroneousSource = indexes: 40..43, file: test)
            UnsupportedConstruct(languageFeature = Indexing, potentialElementSource = indexes: 51..57, file: test, erroneousSource = indexes: 51..57, file: test)
            UnsupportedConstruct(languageFeature = Indexing, potentialElementSource = indexes: 69..72, file: test, erroneousSource = indexes: 69..72, file: test)
            MultipleFailures(
                UnsupportedConstruct(languageFeature = Indexing, potentialElementSource = indexes: 78..81, file: test, erroneousSource = indexes: 78..81, file: test)
                UnsupportedConstruct(languageFeature = Indexing, potentialElementSource = indexes: 81..84, file: test, erroneousSource = indexes: 81..84, file: test)
            )
            UnsupportedConstruct(languageFeature = Indexing, potentialElementSource = indexes: 90..96, file: test, erroneousSource = indexes: 90..96, file: test)
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `rejects mutable vars`() {
        val code = """
            var x = 0
            var x: Int = 0
            var x: Int
            lateinit var x: Int
            """.trimIndent()
        val expected = """
            UnsupportedConstruct(languageFeature = LocalVarNotSupported, potentialElementSource = indexes: 0..9, file: test, erroneousSource = indexes: 0..3, file: test)
            MultipleFailures(
                UnsupportedConstruct(languageFeature = LocalVarNotSupported, potentialElementSource = indexes: 10..24, file: test, erroneousSource = indexes: 10..13, file: test)
                UnsupportedConstruct(languageFeature = ExplicitVariableType, potentialElementSource = indexes: 14..20, file: test, erroneousSource = indexes: 17..20, file: test)
            )
            MultipleFailures(
                UnsupportedConstruct(languageFeature = LocalVarNotSupported, potentialElementSource = indexes: 25..35, file: test, erroneousSource = indexes: 25..28, file: test)
                UnsupportedConstruct(languageFeature = ExplicitVariableType, potentialElementSource = indexes: 29..35, file: test, erroneousSource = indexes: 32..35, file: test)
                UnsupportedConstruct(languageFeature = UninitializedProperty, potentialElementSource = indexes: 25..35, file: test, erroneousSource = indexes: 25..35, file: test)
            )
            MultipleFailures(
                UnsupportedConstruct(languageFeature = ValModifierNotSupported, potentialElementSource = indexes: 36..55, file: test, erroneousSource = indexes: 36..44, file: test)
                UnsupportedConstruct(languageFeature = LocalVarNotSupported, potentialElementSource = indexes: 36..55, file: test, erroneousSource = indexes: 45..48, file: test)
                UnsupportedConstruct(languageFeature = ExplicitVariableType, potentialElementSource = indexes: 49..55, file: test, erroneousSource = indexes: 52..55, file: test)
                UnsupportedConstruct(languageFeature = UninitializedProperty, potentialElementSource = indexes: 36..55, file: test, erroneousSource = indexes: 36..55, file: test)
            )
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `rejects modifiers on vals`() {
        val code = """
            public val x: Int = 0
            private val x: Int
            """.trimIndent()
        val expected = """
            MultipleFailures(
                UnsupportedConstruct(languageFeature = ValModifierNotSupported, potentialElementSource = indexes: 0..21, file: test, erroneousSource = indexes: 0..6, file: test)
                UnsupportedConstruct(languageFeature = ExplicitVariableType, potentialElementSource = indexes: 11..17, file: test, erroneousSource = indexes: 14..17, file: test)
            )
            MultipleFailures(
                UnsupportedConstruct(languageFeature = ValModifierNotSupported, potentialElementSource = indexes: 22..40, file: test, erroneousSource = indexes: 22..29, file: test)
                UnsupportedConstruct(languageFeature = ExplicitVariableType, potentialElementSource = indexes: 34..40, file: test, erroneousSource = indexes: 37..40, file: test)
                UnsupportedConstruct(languageFeature = UninitializedProperty, potentialElementSource = indexes: 22..40, file: test, erroneousSource = indexes: 22..40, file: test)
            )
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `rejects function declarations until they are supported`() {
        val code = """
            fun f() { }
            f { fun local() { } }
            abstract fun f() { }
            """.trimIndent()
        val expected = """
            UnsupportedConstruct(languageFeature = FunctionDeclaration, potentialElementSource = indexes: 0..11, file: test, erroneousSource = indexes: 0..11, file: test)
            UnsupportedConstruct(languageFeature = FunctionDeclaration, potentialElementSource = indexes: 16..31, file: test, erroneousSource = indexes: 16..31, file: test)
            UnsupportedConstruct(languageFeature = FunctionDeclaration, potentialElementSource = indexes: 34..54, file: test, erroneousSource = indexes: 34..54, file: test)
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `rejects annotation usages`() {
        var code = "@A val x = 1"
        var expected = """
            UnsupportedConstruct(languageFeature = AnnotationUsage, potentialElementSource = indexes: 0..12, file: test, erroneousSource = indexes: 0..2, file: test)
            """.trimIndent()
        assertResult(expected, code)

        code = "@A b { }"
        expected = """
            UnsupportedConstruct(languageFeature = AnnotationUsage, potentialElementSource = indexes: 0..8, file: test, erroneousSource = indexes: 0..2, file: test)
            """.trimIndent()
        assertResult(expected, code)

        code = "b(@A x)"
        expected = """
            UnsupportedConstruct(languageFeature = AnnotationUsage, potentialElementSource = indexes: 2..6, file: test, erroneousSource = indexes: 2..4, file: test)
            """.trimIndent()
        assertResult(expected, code)

        code = "b { @A f() }"
        expected = """
            UnsupportedConstruct(languageFeature = AnnotationUsage, potentialElementSource = indexes: 3..10, file: test, erroneousSource = indexes: 3..6, file: test)
            """.trimIndent()
        assertResult(expected, code)
    }
}