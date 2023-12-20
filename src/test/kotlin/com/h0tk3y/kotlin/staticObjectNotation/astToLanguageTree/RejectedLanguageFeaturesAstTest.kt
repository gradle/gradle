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
            """
            UnsupportedConstruct(
                languageFeature = PackageHeader, 
                potentialElementSource = indexes: 0..19, line/column: 1/1..1/25, file: test, 
                erroneousSource = indexes: 0..19, line/column: 1/1..1/25, file: test
            )
            """.trimIndent()
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
            UnsupportedConstruct(
                languageFeature = StarImport, 
                potentialElementSource = indexes: 0..15, line/column: 1/1..1/16, file: test, 
                erroneousSource = indexes: 13..14, line/column: 1/14..1/15, file: test
            )
            UnsupportedConstruct(
                languageFeature = StarImport, 
                potentialElementSource = indexes: 15..26, line/column: 2/1..2/12, file: test, 
                erroneousSource = indexes: 24..25, line/column: 2/10..2/11, file: test
            )
            UnsupportedConstruct(
                languageFeature = StarImport, 
                potentialElementSource = indexes: 26..48, line/column: 3/1..3/28, file: test, 
                erroneousSource = indexes: 47..48, line/column: 3/22..3/23, file: test
            )
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
            UnsupportedConstruct(
                languageFeature = RenamingImport, 
                potentialElementSource = indexes: 0..14, line/column: 1/1..1/15, file: test, 
                erroneousSource = indexes: 9..13, line/column: 1/10..1/14, file: test
            )
            UnsupportedConstruct(
                languageFeature = RenamingImport, 
                potentialElementSource = indexes: 14..29, line/column: 2/1..2/21, file: test, 
                erroneousSource = indexes: 25..29, line/column: 2/12..2/16, file: test
            )
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
            UnsupportedConstruct(
                languageFeature = TypeDeclaration, 
                potentialElementSource = indexes: 0..7, line/column: 1/1..1/8, file: test, 
                erroneousSource = indexes: 0..7, line/column: 1/1..1/8, file: test
            )
            UnsupportedConstruct(
                languageFeature = TypeDeclaration, 
                potentialElementSource = indexes: 8..19, line/column: 2/1..2/12, file: test, 
                erroneousSource = indexes: 8..19, line/column: 2/1..2/12, file: test
            )
            UnsupportedConstruct(
                languageFeature = TypeDeclaration, 
                potentialElementSource = indexes: 20..35, line/column: 3/1..3/16, file: test, 
                erroneousSource = indexes: 20..35, line/column: 3/1..3/16, file: test
            )
            UnsupportedConstruct(
                languageFeature = TypeDeclaration, 
                potentialElementSource = indexes: 36..58, line/column: 4/1..4/23, file: test, 
                erroneousSource = indexes: 36..58, line/column: 4/1..4/23, file: test
            )
            UnsupportedConstruct(
                languageFeature = TypeDeclaration, 
                potentialElementSource = indexes: 59..77, line/column: 5/1..5/19, file: test, 
                erroneousSource = indexes: 59..77, line/column: 5/1..5/19, file: test
            )
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `rejects variable type labels`() {
        val code = """
            val a: Int = 0
            """.trimIndent()
        val expected = """
            UnsupportedConstruct(
                languageFeature = ExplicitVariableType, 
                potentialElementSource = indexes: 4..10, line/column: 1/5..1/11, file: test, 
                erroneousSource = indexes: 7..10, line/column: 1/8..1/11, file: test
            )
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
            UnsupportedConstruct(
                languageFeature = Indexing, 
                potentialElementSource = indexes: 1..4, line/column: 1/2..1/5, file: test, 
                erroneousSource = indexes: 1..4, line/column: 1/2..1/5, file: test
            )
            UnsupportedConstruct(
                languageFeature = Indexing, 
                potentialElementSource = indexes: 14..17, line/column: 2/6..2/9, file: test, 
                erroneousSource = indexes: 14..17, line/column: 2/6..2/9, file: test
            )
            UnsupportedConstruct(
                languageFeature = Indexing, 
                potentialElementSource = indexes: 23..26, line/column: 3/2..3/5, file: test, 
                erroneousSource = indexes: 23..26, line/column: 3/2..3/5, file: test
            )
            UnsupportedConstruct(
                languageFeature = Indexing, 
                potentialElementSource = indexes: 40..43, line/column: 4/8..4/11, file: test, 
                erroneousSource = indexes: 40..43, line/column: 4/8..4/11, file: test
            )
            UnsupportedConstruct(
                languageFeature = Indexing, 
                potentialElementSource = indexes: 51..57, line/column: 5/4..5/10, file: test, 
                erroneousSource = indexes: 51..57, line/column: 5/4..5/10, file: test
            )
            UnsupportedConstruct(
                languageFeature = Indexing, 
                potentialElementSource = indexes: 69..72, line/column: 6/6..6/9, file: test, 
                erroneousSource = indexes: 69..72, line/column: 6/6..6/9, file: test
            )
            MultipleFailures(
                UnsupportedConstruct(
                    languageFeature = Indexing, 
                    potentialElementSource = indexes: 78..81, line/column: 7/6..7/9, file: test, 
                    erroneousSource = indexes: 78..81, line/column: 7/6..7/9, file: test
                )
                UnsupportedConstruct(
                    languageFeature = Indexing, 
                    potentialElementSource = indexes: 81..84, line/column: 7/9..7/12, file: test, 
                    erroneousSource = indexes: 81..84, line/column: 7/9..7/12, file: test
                )
            )
            UnsupportedConstruct(
                languageFeature = Indexing, 
                potentialElementSource = indexes: 90..96, line/column: 8/6..8/12, file: test, 
                erroneousSource = indexes: 90..96, line/column: 8/6..8/12, file: test
            )
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
            UnsupportedConstruct(
                languageFeature = LocalVarNotSupported, 
                potentialElementSource = indexes: 0..9, line/column: 1/1..1/10, file: test, 
                erroneousSource = indexes: 0..3, line/column: 1/1..1/4, file: test
            )
            MultipleFailures(
                UnsupportedConstruct(
                    languageFeature = LocalVarNotSupported, 
                    potentialElementSource = indexes: 10..24, line/column: 2/1..2/15, file: test, 
                    erroneousSource = indexes: 10..13, line/column: 2/1..2/4, file: test
                )
                UnsupportedConstruct(
                    languageFeature = ExplicitVariableType, 
                    potentialElementSource = indexes: 14..20, line/column: 2/5..2/11, file: test, 
                    erroneousSource = indexes: 17..20, line/column: 2/8..2/11, file: test
                )
            )
            MultipleFailures(
                UnsupportedConstruct(
                    languageFeature = LocalVarNotSupported, 
                    potentialElementSource = indexes: 25..35, line/column: 3/1..3/11, file: test, 
                    erroneousSource = indexes: 25..28, line/column: 3/1..3/4, file: test
                )
                UnsupportedConstruct(
                    languageFeature = ExplicitVariableType, 
                    potentialElementSource = indexes: 29..35, line/column: 3/5..3/11, file: test, 
                    erroneousSource = indexes: 32..35, line/column: 3/8..3/11, file: test
                )
                UnsupportedConstruct(
                    languageFeature = UninitializedProperty, 
                    potentialElementSource = indexes: 25..35, line/column: 3/1..3/11, file: test, 
                    erroneousSource = indexes: 25..35, line/column: 3/1..3/11, file: test
                )
            )
            MultipleFailures(
                UnsupportedConstruct(
                    languageFeature = ValModifierNotSupported, 
                    potentialElementSource = indexes: 36..55, line/column: 4/1..4/20, file: test, 
                    erroneousSource = indexes: 36..44, line/column: 4/1..4/9, file: test
                )
                UnsupportedConstruct(
                    languageFeature = LocalVarNotSupported, 
                    potentialElementSource = indexes: 36..55, line/column: 4/1..4/20, file: test, 
                    erroneousSource = indexes: 45..48, line/column: 4/10..4/13, file: test
                )
                UnsupportedConstruct(
                    languageFeature = ExplicitVariableType, 
                    potentialElementSource = indexes: 49..55, line/column: 4/14..4/20, file: test, 
                    erroneousSource = indexes: 52..55, line/column: 4/17..4/20, file: test
                )
                UnsupportedConstruct(
                    languageFeature = UninitializedProperty, 
                    potentialElementSource = indexes: 36..55, line/column: 4/1..4/20, file: test, 
                    erroneousSource = indexes: 36..55, line/column: 4/1..4/20, file: test
                )
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
                UnsupportedConstruct(
                    languageFeature = ValModifierNotSupported, 
                    potentialElementSource = indexes: 0..21, line/column: 1/1..1/22, file: test, 
                    erroneousSource = indexes: 0..6, line/column: 1/1..1/7, file: test
                )
                UnsupportedConstruct(
                    languageFeature = ExplicitVariableType, 
                    potentialElementSource = indexes: 11..17, line/column: 1/12..1/18, file: test, 
                    erroneousSource = indexes: 14..17, line/column: 1/15..1/18, file: test
                )
            )
            MultipleFailures(
                UnsupportedConstruct(
                    languageFeature = ValModifierNotSupported, 
                    potentialElementSource = indexes: 22..40, line/column: 2/1..2/19, file: test, 
                    erroneousSource = indexes: 22..29, line/column: 2/1..2/8, file: test
                )
                UnsupportedConstruct(
                    languageFeature = ExplicitVariableType, 
                    potentialElementSource = indexes: 34..40, line/column: 2/13..2/19, file: test, 
                    erroneousSource = indexes: 37..40, line/column: 2/16..2/19, file: test
                )
                UnsupportedConstruct(
                    languageFeature = UninitializedProperty, 
                    potentialElementSource = indexes: 22..40, line/column: 2/1..2/19, file: test, 
                    erroneousSource = indexes: 22..40, line/column: 2/1..2/19, file: test
                )
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
            UnsupportedConstruct(
                languageFeature = FunctionDeclaration, 
                potentialElementSource = indexes: 0..11, line/column: 1/1..1/12, file: test, 
                erroneousSource = indexes: 0..11, line/column: 1/1..1/12, file: test
            )
            UnsupportedConstruct(
                languageFeature = FunctionDeclaration, 
                potentialElementSource = indexes: 16..31, line/column: 2/5..2/20, file: test, 
                erroneousSource = indexes: 16..31, line/column: 2/5..2/20, file: test
            )
            UnsupportedConstruct(
                languageFeature = FunctionDeclaration, 
                potentialElementSource = indexes: 34..54, line/column: 3/1..3/21, file: test, 
                erroneousSource = indexes: 34..54, line/column: 3/1..3/21, file: test
            )
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `rejects annotation usages`() {
        var code = "@A val x = 1"
        var expected = """
            UnsupportedConstruct(
                languageFeature = AnnotationUsage, 
                potentialElementSource = indexes: 0..12, line/column: 1/1..1/13, file: test, 
                erroneousSource = indexes: 0..2, line/column: 1/1..1/3, file: test
            )
            """.trimIndent()
        assertResult(expected, code)

        code = "@A b { }"
        expected = """
            UnsupportedConstruct(
                languageFeature = AnnotationUsage, 
                potentialElementSource = indexes: 0..8, line/column: 1/1..1/9, file: test, 
                erroneousSource = indexes: 0..2, line/column: 1/1..1/3, file: test
            )
            """.trimIndent()
        assertResult(expected, code)

        code = "b(@A x)"
        expected = """
            UnsupportedConstruct(
                languageFeature = AnnotationUsage, 
                potentialElementSource = indexes: 2..6, line/column: 1/3..1/7, file: test, 
                erroneousSource = indexes: 2..4, line/column: 1/3..1/5, file: test
            )
            """.trimIndent()
        assertResult(expected, code)

        code = "b { @A f() }"
        expected = """
            UnsupportedConstruct(
                languageFeature = AnnotationUsage, 
                potentialElementSource = indexes: 3..10, line/column: 1/4..1/11, file: test, 
                erroneousSource = indexes: 3..6, line/column: 1/4..1/7, file: test
            )
            """.trimIndent()
        assertResult(expected, code)
    }

    @Test
    fun `rejects augmenting assignments`() {
        val code = """
            a += b
            a.x -= b
            """.trimIndent()
        val expected = """
            xxx
            """.trimIndent()
        assertResult(expected, code)
    }
}