package org.gradle.kotlin.dsl.support

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class ImplicitImportsTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `implicit imports are fully qualified to allow use of the preferred type amongst those with same simple name in different Gradle API packages`() {

        // given:
        withBuildScript(
            """

            println("*" + Jar::class.qualifiedName + "*")

            """
        )

        // when:
        val result = build("help")

        // then:
        assertThat(result.output, containsString("*org.gradle.api.tasks.bundling.Jar*"))
    }

    @Test
    fun `can use kotlin-dsl implicit imports`() {

        withBuildScript(
            """
            val a = Callable { "some" }
            val b = TimeUnit.DAYS
            val c = File("some")
            val d = BigDecimal.ONE
            val e = BigInteger.ONE

            open class Foo {
                @Inject
                constructor() {}
            }
            """
        )

        build("help")
    }
}
