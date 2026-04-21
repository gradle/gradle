package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest

import org.gradle.util.GradleVersion
import org.hamcrest.CoreMatchers.containsString

import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class DelegatedExtraPropertiesIntegrationTest : AbstractKotlinIntegrationTest() {

    private
    fun expectDeprecation(feature: String, advice: String) {
        executer.expectDocumentedDeprecationWarning(
            "$feature has been deprecated. " +
                "This is scheduled to be removed in Gradle 10. " +
                "$advice " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#kotlin_dsl_delegated_properties"
        )
    }

    @Test
    fun `non-nullable delegated extra property access of non-existing extra property throws`() {

        withBuildScript(
            """
            @file:Suppress("DEPRECATION")

            val myTask = task("myTask") {}
            val foo: Int by myTask.extra
            foo.toString()
            """
        )

        expectDeprecation(
            "The 'val name: Type by extra' property delegate syntax",
            "Use 'val property = extra[name] as Type' instead."
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
            @file:Suppress("DEPRECATION")

            val myTask = task("myTask") {
                val foo: Int? by extra { null }
            }
            val foo: Int by myTask.extra
            foo.toString()
            """
        )

        expectDeprecation(
            "The 'val name by extra(...)' or 'val name by extra { ... }' property delegate syntax",
            "Use 'extra.set(name, value)' instead."
        )
        expectDeprecation(
            "The 'val name: Type by extra' property delegate syntax",
            "Use 'val property = extra[name] as Type' instead."
        )

        assertThat(
            buildAndFail("myTask").error,
            containsString("Cannot get non-null extra property 'foo' as it is null")
        )
    }
}
