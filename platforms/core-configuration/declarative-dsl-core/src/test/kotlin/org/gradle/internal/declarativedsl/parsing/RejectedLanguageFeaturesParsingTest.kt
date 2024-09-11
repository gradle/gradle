package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.LanguageTreeResult
import org.gradle.internal.declarativedsl.prettyPrintLanguageResult
import org.junit.Test


class RejectedLanguageFeaturesParsingTest {

    @Test
    fun `rejects explicit package declaration`() {
        val code = "package com.example"
        val expected =
            """
            UnsupportedConstruct(
                languageFeature = PackageHeader,
                potentialElementSource = indexes: 0..19, line/column: 1/1..1/20, file: test,
                erroneousSource = indexes: 0..19, line/column: 1/1..1/20, file: test
            )
            """.trimIndent()
        parse(code).assert(expected) { result: LanguageTreeResult -> result.headerFailures.joinToString { prettyPrintLanguageResult(it) } }
    }

    @Test
    fun `rejects star imports`() {
        val code = """
            import a.b.c.*
            import a.*
            import a.A.Companion.*""".trimIndent()
        val expected = """
            UnsupportedConstruct(
                languageFeature = StarImport,
                potentialElementSource = indexes: 0..14, line/column: 1/1..1/15, file: test,
                erroneousSource = indexes: 13..14, line/column: 1/14..1/15, file: test
            )
            UnsupportedConstruct(
                languageFeature = StarImport,
                potentialElementSource = indexes: 15..25, line/column: 2/1..2/11, file: test,
                erroneousSource = indexes: 24..25, line/column: 2/10..2/11, file: test
            )
            UnsupportedConstruct(
                languageFeature = StarImport,
                potentialElementSource = indexes: 26..48, line/column: 3/1..3/23, file: test,
                erroneousSource = indexes: 47..48, line/column: 3/22..3/23, file: test
            )""".trimIndent()
        parse(code).assert(expected) { result: LanguageTreeResult -> result.headerFailures.joinToString("\n") { prettyPrintLanguageResult(it) } }
    }

    @Test
    fun `rejects renaming imports`() {
        val code = """
            import a as b
            import a.A as B""".trimIndent()
        val expected = """
            UnsupportedConstruct(
                languageFeature = RenamingImport,
                potentialElementSource = indexes: 0..13, line/column: 1/1..1/14, file: test,
                erroneousSource = indexes: 9..13, line/column: 1/10..1/14, file: test
            )
            UnsupportedConstruct(
                languageFeature = RenamingImport,
                potentialElementSource = indexes: 14..29, line/column: 2/1..2/16, file: test,
                erroneousSource = indexes: 25..29, line/column: 2/12..2/16, file: test
            )""".trimIndent()
        parse(code).assert(expected) { result: LanguageTreeResult -> result.headerFailures.joinToString("\n") { prettyPrintLanguageResult(it) } }
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
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = TypeDeclaration,
                    potentialElementSource = indexes: 0..7, line/column: 1/1..1/8, file: test,
                    erroneousSource = indexes: 0..7, line/column: 1/1..1/8, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = TypeDeclaration,
                    potentialElementSource = indexes: 8..19, line/column: 2/1..2/12, file: test,
                    erroneousSource = indexes: 8..19, line/column: 2/1..2/12, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = TypeDeclaration,
                    potentialElementSource = indexes: 20..35, line/column: 3/1..3/16, file: test,
                    erroneousSource = indexes: 20..35, line/column: 3/1..3/16, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = TypeDeclaration,
                    potentialElementSource = indexes: 36..58, line/column: 4/1..4/23, file: test,
                    erroneousSource = indexes: 36..58, line/column: 4/1..4/23, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = TypeDeclaration,
                    potentialElementSource = indexes: 59..77, line/column: 5/1..5/19, file: test,
                    erroneousSource = indexes: 59..77, line/column: 5/1..5/19, file: test
                )
            )
            """.trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `rejects variable type labels`() {
        val code = """
            val a: Int = 0""".trimIndent()
        val expected = """
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = ExplicitVariableType,
                    potentialElementSource = indexes: 0..14, line/column: 1/1..1/15, file: test,
                    erroneousSource = indexes: 7..10, line/column: 1/8..1/11, file: test
                )
            )""".trimIndent()
        parse(code).assert(expected)
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
            a = b[b[1]]""".trimIndent()
        val expected = """
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = Indexing,
                    potentialElementSource = indexes: 0..4, line/column: 1/1..1/5, file: test,
                    erroneousSource = indexes: 0..4, line/column: 1/1..1/5, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = Indexing,
                    potentialElementSource = indexes: 9..17, line/column: 2/1..2/9, file: test,
                    erroneousSource = indexes: 9..17, line/column: 2/1..2/9, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = Indexing,
                    potentialElementSource = indexes: 22..26, line/column: 3/1..3/5, file: test,
                    erroneousSource = indexes: 22..26, line/column: 3/1..3/5, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = Indexing,
                    potentialElementSource = indexes: 33..43, line/column: 4/1..4/11, file: test,
                    erroneousSource = indexes: 33..43, line/column: 4/1..4/11, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = Indexing,
                    potentialElementSource = indexes: 48..57, line/column: 5/1..5/10, file: test,
                    erroneousSource = indexes: 48..57, line/column: 5/1..5/10, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = Indexing,
                    potentialElementSource = indexes: 68..72, line/column: 6/5..6/9, file: test,
                    erroneousSource = indexes: 68..72, line/column: 6/5..6/9, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = Indexing,
                    potentialElementSource = indexes: 77..84, line/column: 7/5..7/12, file: test,
                    erroneousSource = indexes: 77..84, line/column: 7/5..7/12, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = Indexing,
                    potentialElementSource = indexes: 89..96, line/column: 8/5..8/12, file: test,
                    erroneousSource = indexes: 89..96, line/column: 8/5..8/12, file: test
                )
            )""".trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `rejects mutable vars`() {
        val code = """
            var x = 0
            var x: Int = 0
            var x: Int
            lateinit var x: Int""".trimIndent()
        val expected = """
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = LocalVarNotSupported,
                    potentialElementSource = indexes: 0..9, line/column: 1/1..1/10, file: test,
                    erroneousSource = indexes: 0..3, line/column: 1/1..1/4, file: test
                )
            )
            ErroneousStatement (
                MultipleFailures(
                    UnsupportedConstruct(
                        languageFeature = LocalVarNotSupported,
                        potentialElementSource = indexes: 10..24, line/column: 2/1..2/15, file: test,
                        erroneousSource = indexes: 10..13, line/column: 2/1..2/4, file: test
                    )
                    UnsupportedConstruct(
                        languageFeature = ExplicitVariableType,
                        potentialElementSource = indexes: 10..24, line/column: 2/1..2/15, file: test,
                        erroneousSource = indexes: 17..20, line/column: 2/8..2/11, file: test
                    )
                )
            )
            ErroneousStatement (
                MultipleFailures(
                    UnsupportedConstruct(
                        languageFeature = LocalVarNotSupported,
                        potentialElementSource = indexes: 25..35, line/column: 3/1..3/11, file: test,
                        erroneousSource = indexes: 25..28, line/column: 3/1..3/4, file: test
                    )
                    UnsupportedConstruct(
                        languageFeature = ExplicitVariableType,
                        potentialElementSource = indexes: 25..35, line/column: 3/1..3/11, file: test,
                        erroneousSource = indexes: 32..35, line/column: 3/8..3/11, file: test
                    )
                    UnsupportedConstruct(
                        languageFeature = UninitializedProperty,
                        potentialElementSource = indexes: 25..35, line/column: 3/1..3/11, file: test,
                        erroneousSource = indexes: 25..35, line/column: 3/1..3/11, file: test
                    )
                )
            )
            ErroneousStatement (
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
                        potentialElementSource = indexes: 36..55, line/column: 4/1..4/20, file: test,
                        erroneousSource = indexes: 52..55, line/column: 4/17..4/20, file: test
                    )
                    UnsupportedConstruct(
                        languageFeature = UninitializedProperty,
                        potentialElementSource = indexes: 36..55, line/column: 4/1..4/20, file: test,
                        erroneousSource = indexes: 36..55, line/column: 4/1..4/20, file: test
                    )
                )
            )""".trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `rejects modifiers on vals`() {
        val code = """
            public val x: Int = 0
            private val x: Int""".trimIndent()
        val expected = """
        ErroneousStatement (
            MultipleFailures(
                UnsupportedConstruct(
                    languageFeature = ValModifierNotSupported,
                    potentialElementSource = indexes: 0..21, line/column: 1/1..1/22, file: test,
                    erroneousSource = indexes: 0..6, line/column: 1/1..1/7, file: test
                )
                UnsupportedConstruct(
                    languageFeature = ExplicitVariableType,
                    potentialElementSource = indexes: 0..21, line/column: 1/1..1/22, file: test,
                    erroneousSource = indexes: 14..17, line/column: 1/15..1/18, file: test
                )
            )
        )
        ErroneousStatement (
            MultipleFailures(
                UnsupportedConstruct(
                    languageFeature = ValModifierNotSupported,
                    potentialElementSource = indexes: 22..40, line/column: 2/1..2/19, file: test,
                    erroneousSource = indexes: 22..29, line/column: 2/1..2/8, file: test
                )
                UnsupportedConstruct(
                    languageFeature = ExplicitVariableType,
                    potentialElementSource = indexes: 22..40, line/column: 2/1..2/19, file: test,
                    erroneousSource = indexes: 37..40, line/column: 2/16..2/19, file: test
                )
                UnsupportedConstruct(
                    languageFeature = UninitializedProperty,
                    potentialElementSource = indexes: 22..40, line/column: 2/1..2/19, file: test,
                    erroneousSource = indexes: 22..40, line/column: 2/1..2/19, file: test
                )
            )
        )
        """.trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `rejects function declarations until they are supported`() {
        val code = """
            fun f() { }
            f { fun local() { } }
            abstract fun f() { }""".trimIndent()
        val expected = """
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = FunctionDeclaration,
                    potentialElementSource = indexes: 0..11, line/column: 1/1..1/12, file: test,
                    erroneousSource = indexes: 0..11, line/column: 1/1..1/12, file: test
                )
            )
            FunctionCall [indexes: 12..33, line/column: 2/1..2/22, file: test] (
                name = f
                args = [
                    FunctionArgument.Lambda [indexes: 14..33, line/column: 2/3..2/22, file: test] (
                        block = Block [indexes: 16..31, line/column: 2/5..2/20, file: test] (
                            ErroneousStatement (
                                UnsupportedConstruct(
                                    languageFeature = FunctionDeclaration,
                                    potentialElementSource = indexes: 16..31, line/column: 2/5..2/20, file: test,
                                    erroneousSource = indexes: 16..31, line/column: 2/5..2/20, file: test
                                )
                            )
                        )
                    )
                ]
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = FunctionDeclaration,
                    potentialElementSource = indexes: 34..54, line/column: 3/1..3/21, file: test,
                    erroneousSource = indexes: 34..54, line/column: 3/1..3/21, file: test
                )
            )""".trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `rejects annotation usages`() {
        val code = """
            @A x = 1
            @A b { }
            b(@A x)
            b { @A f() }
        """.trimIndent()
        val expected = """
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = AnnotationUsage,
                    potentialElementSource = indexes: 0..4, line/column: 1/1..1/5, file: test,
                    erroneousSource = indexes: 0..4, line/column: 1/1..1/5, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = AnnotationUsage,
                    potentialElementSource = indexes: 9..17, line/column: 2/1..2/9, file: test,
                    erroneousSource = indexes: 9..17, line/column: 2/1..2/9, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = AnnotationUsage,
                    potentialElementSource = indexes: 20..24, line/column: 3/3..3/7, file: test,
                    erroneousSource = indexes: 20..24, line/column: 3/3..3/7, file: test
                )
            )
            FunctionCall [indexes: 26..38, line/column: 4/1..4/13, file: test] (
                name = b
                args = [
                    FunctionArgument.Lambda [indexes: 28..38, line/column: 4/3..4/13, file: test] (
                        block = Block [indexes: 30..36, line/column: 4/5..4/11, file: test] (
                            ErroneousStatement (
                                UnsupportedConstruct(
                                    languageFeature = AnnotationUsage,
                                    potentialElementSource = indexes: 30..36, line/column: 4/5..4/11, file: test,
                                    erroneousSource = indexes: 30..36, line/column: 4/5..4/11, file: test
                                )
                            )
                        )
                    )
                ]
            )""".trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `rejects augmenting assignments`() {
        val code = """
            a += b
            a.x -= b""".trimIndent()
        val expected = """
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 0..6, line/column: 1/1..1/7, file: test,
                    erroneousSource = indexes: 0..6, line/column: 1/1..1/7, file: test
                )
            )
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 7..15, line/column: 2/1..2/9, file: test,
                    erroneousSource = indexes: 7..15, line/column: 2/1..2/9, file: test
                )
            )""".trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `reject unsupported binary expression`() {
        val code =
            """
            a = (1 + 2)
            """.trimIndent()

        val expected = """
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = UnsupportedOperationInBinaryExpression,
                    potentialElementSource = indexes: 5..10, line/column: 1/6..1/11, file: test,
                    erroneousSource = indexes: 5..10, line/column: 1/6..1/11, file: test
                )
            )""".trimIndent()
        parse(code).assert(expected)
    }

    @Test
    fun `rejects lambda literal`() {
        val code =
            """
            call(x = { })
            multiLambda({ }, { })
            """.trimIndent()
        val expected = """
            ErroneousStatement (
                UnsupportedConstruct(
                    languageFeature = FunctionDeclaration,
                    potentialElementSource = indexes: 9..12, line/column: 1/10..1/13, file: test,
                    erroneousSource = indexes: 9..12, line/column: 1/10..1/13, file: test
                )
            )
            ErroneousStatement (
                MultipleFailures(
                    UnsupportedConstruct(
                        languageFeature = FunctionDeclaration,
                        potentialElementSource = indexes: 26..29, line/column: 2/13..2/16, file: test,
                        erroneousSource = indexes: 26..29, line/column: 2/13..2/16, file: test
                    )
                    UnsupportedConstruct(
                        languageFeature = FunctionDeclaration,
                        potentialElementSource = indexes: 31..34, line/column: 2/18..2/21, file: test,
                        erroneousSource = indexes: 31..34, line/column: 2/18..2/21, file: test
                    )
                )
            )""".trimIndent()
        parse(code).assert(expected)
    }

    private
    fun parse(code: String): LanguageTreeResult = ParseTestUtil.parse(code)
}
