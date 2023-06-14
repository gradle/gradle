package org.gradle.kotlin.dsl.template

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver.EnvironmentProperties
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File


class KotlinBuildScriptTemplateAdditionalCompilerArgumentsProviderTest : TestWithTempFiles() {

    private
    val argumentProvider = KotlinBuildScriptTemplateAdditionalCompilerArgumentsProvider(emptyList())

    @Test
    fun `kotlin assignment is ENABLED when environment is null`() {
        // When
        val result = argumentProvider.getAdditionalCompilerArguments(null)

        // Expect
        assertThat(result, equalTo(listOf("-P=plugin:org.jetbrains.kotlin.assignment:annotation=org.gradle.api.SupportsKotlinAssignmentOverloading")))
    }

    @Test
    fun `kotlin assignment is ENABLED when root file is not in the project`() {
        // Given
        val environment = mapOf<String, Any?>()

        // When
        val result = argumentProvider.getAdditionalCompilerArguments(environment)

        // Expect
        assertThat(result, equalTo(listOf("-P=plugin:org.jetbrains.kotlin.assignment:annotation=org.gradle.api.SupportsKotlinAssignmentOverloading")))
    }

    @Test
    fun `kotlin assignment is ENABLED when gradle-properties file does not exist`() {
        // Given
        val environment = mapOf<String, Any?>(EnvironmentProperties.projectRoot to root)

        // When
        val result = argumentProvider.getAdditionalCompilerArguments(environment)

        // Expect
        assertThat(result, equalTo(listOf("-P=plugin:org.jetbrains.kotlin.assignment:annotation=org.gradle.api.SupportsKotlinAssignmentOverloading")))
    }

    @Test
    fun `kotlin assignment is ENABLED when gradle-properties exists`() {
        // Given
        File(root, "gradle.properties").writeText("some.prop=some.value")
        val environment = mapOf<String, Any?>(EnvironmentProperties.projectRoot to root)

        // When
        val result = argumentProvider.getAdditionalCompilerArguments(environment)

        // Expect
        assertThat(result, equalTo(listOf("-P=plugin:org.jetbrains.kotlin.assignment:annotation=org.gradle.api.SupportsKotlinAssignmentOverloading")))
    }

    @Test
    fun `kotlin assignment is ENABLED when it's enabled in gradle-properties`() {
        // Given
        File(root, "gradle.properties").writeText("systemProp.org.gradle.unsafe.kotlin.assignment=true")
        val environment = mapOf<String, Any?>(EnvironmentProperties.projectRoot to root)

        // When
        val result = argumentProvider.getAdditionalCompilerArguments(environment)

        // Expect
        assertThat(result, equalTo(listOf("-P=plugin:org.jetbrains.kotlin.assignment:annotation=org.gradle.api.SupportsKotlinAssignmentOverloading")))
    }

    @Test
    fun `kotlin assignment is DISABLED when it's disabled in gradle-properties`() {
        // Given
        File(root, "gradle.properties").writeText("systemProp.org.gradle.unsafe.kotlin.assignment=false")
        val environment = mapOf<String, Any?>(EnvironmentProperties.projectRoot to root)

        // When
        val result = argumentProvider.getAdditionalCompilerArguments(environment)

        // Expect
        assertThat(result, equalTo(listOf()))
    }
}
