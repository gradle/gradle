package org.gradle.kotlin.dsl.support

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test

class ImplicitImportsTest : AbstractIntegrationTest() {

    @Test
    fun `implicit imports are fully qualified to allow use of the preferred type amongst those with same simple name in different Gradle API packages`() {

        // given:
        withBuildScript("""

            println("*" + Jar::class.qualifiedName + "*")

        """)

        // when:
        val result = build("help")

        // then:
        assertThat(result.output, containsString("*org.gradle.api.tasks.bundling.Jar*"))
    }
}
