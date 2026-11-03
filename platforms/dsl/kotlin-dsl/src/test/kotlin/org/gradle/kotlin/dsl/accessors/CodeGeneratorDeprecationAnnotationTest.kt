package org.gradle.kotlin.dsl.accessors

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class CodeGeneratorDeprecationAnnotationTest {

    @Test
    fun `#maybeDeprecationAnnotations renders nothing without a deprecation`() {
        assertThat(
            maybeDeprecationAnnotations(null),
            equalTo("")
        )
    }

    @Test
    fun `#maybeDeprecationAnnotations renders WARNING level without margin markers`() {
        assertThat(
            maybeDeprecationAnnotations(Deprecated(message = "Just don't", level = DeprecationLevel.WARNING)),
            equalTo("@Suppress(\"deprecation\")\n        @Deprecated(\"Just don't\", level = DeprecationLevel.WARNING)\n        ")
        )
    }

    @Test
    fun `#maybeDeprecationAnnotations renders ERROR level without margin markers`() {
        val annotations = maybeDeprecationAnnotations(Deprecated(message = "Just don't", level = DeprecationLevel.ERROR))

        // The bug left the raw `|` margin markers in the output, producing invalid Kotlin.
        assertThat(
            annotations,
            startsWith("@Suppress(\"DEPRECATION_ERROR\")\n")
        )
        assertThat(
            annotations,
            equalTo("@Suppress(\"DEPRECATION_ERROR\")\n        @Deprecated(\"Just don't\", level = DeprecationLevel.ERROR)\n        ")
        )
    }

    @Test
    fun `#maybeDeprecationAnnotations ERROR matches WARNING formatting modulo the suppress string`() {
        val warning = maybeDeprecationAnnotations(Deprecated(message = "Just don't", level = DeprecationLevel.WARNING))
        val error = maybeDeprecationAnnotations(Deprecated(message = "Just don't", level = DeprecationLevel.ERROR))

        assertThat(
            error
                .replace("DEPRECATION_ERROR", "deprecation")
                .replace("DeprecationLevel.ERROR", "DeprecationLevel.WARNING"),
            equalTo(warning)
        )
    }
}
