package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest

import org.hamcrest.CoreMatchers.containsString

import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class DelegatedExtraPropertiesIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `non-nullable delegated extra property access of non-existing extra property throws`() {

        withBuildScript(
            """
            val myTask = task("myTask") {}
            val foo: Int by myTask.extra
            foo.toString()
            """
        )

        assertThat(
            buildAndFail("myTask").error,
            containsString("Cannot get non-null extra property 'foo' as it does not exist")
        )
    }

    @Test
    fun `non-nullable delegated extra property access of existing null extra property throws`() {

        withBuildScript(
            """
            val myTask = task("myTask") {
                val foo: Int? by extra { null }
            }
            val foo: Int by myTask.extra
            foo.toString()
            """
        )

        assertThat(
            buildAndFail("myTask").error,
            containsString("Cannot get non-null extra property 'foo' as it is null")
        )
    }
}
