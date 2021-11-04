package org.gradle.kotlin.dsl.support

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.junit.Test
import spock.lang.Issue


class KotlinScriptCompilerIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @Issue("https://github.com/gradle/gradle/issues/18714")
    fun `Build scripts are compiled with jsr305 strict mode`() {

        // given:
        withFile(
            "buildSrc/src/main/java/StringTransformer.java",
            """
                @org.gradle.api.NonNullApi
                public interface StringTransformer {
                    String transform(String input);
                }
            """
        )

        withBuildScript("StringTransformer { input -> null }")

        // when:
        val result = buildAndFail("help")

        // then:
        result.assertHasDescription("Script compilation error")
        result.hasErrorOutput("Null can not be a value of a non-null type String")
    }
}
