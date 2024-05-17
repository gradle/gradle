package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest

import org.hamcrest.CoreMatchers.containsString

import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


/**
 * See https://docs.gradle.org/current/userguide/build_environment.html
 */
class DelegatedGradlePropertiesIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `non-nullable delegated property access of non-existing gradle property throws`() {

        withSettings(
            """
            val nonExisting: String by settings
            println(nonExisting)
            """
        )

        assertThat(
            buildAndFail("help").error,
            containsString("Cannot get non-null property 'nonExisting' on settings '${projectRoot.name}' as it does not exist")
        )

        withSettings("")
        withBuildScript(
            """
            val nonExisting: String by project
            println(nonExisting)
            """
        )

        assertThat(
            buildAndFail("help").error,
            containsString("Cannot get non-null property 'nonExisting' on root project '${projectRoot.name}' as it does not exist")
        )
    }

    @Test
    fun `delegated properties follow Gradle mechanics and allow to model optional properties via nullable kotlin types`() {

        // given: build root gradle.properties file
        withFile(
            "gradle.properties",
            """
            setBuildProperty=build value
            emptyBuildProperty=

            userHomeOverriddenBuildProperty=build value
            cliOverriddenBuildProperty=build value

            projectMutatedBuildProperty=build value
            """.trimIndent()
        )

        // and: gradle user home gradle.properties file
        withFile(
            "gradle-user-home/gradle.properties",
            """
            setUserHomeProperty=user home value
            emptyUserHomeProperty=

            userHomeOverriddenBuildProperty=user home value
            cliOverriddenUserHomeProperty=user home value

            projectMutatedUserHomeProperty=user home value
            """.trimIndent()
        )

        // and: isolated gradle user home
        executer.withGradleUserHomeDir(existing("gradle-user-home"))
        executer.requireIsolatedDaemons()

        // and: gradle command line with properties
        val buildArguments = arrayOf(
            "-PsetCliProperty=cli value",
            "-PemptyCliProperty=",
            "-PcliOverriddenBuildProperty=cli value",
            "-PcliOverriddenUserHomeProperty=cli value",
            "-Dorg.gradle.project.setOrgGradleProjectSystemProperty=system property value",
            "-Dorg.gradle.project.emptyOrgGradleProjectSystemProperty=",
            "help"
        )

        // when: both settings and project scripts asserting on delegated properties
        withSettings(requirePropertiesFromSettings())
        withBuildScript(requirePropertiesFromProject())

        // then:
        build(*buildArguments)

        // when: project script buildscript block asserting on delegated properties
        withSettings("")
        withBuildScript(
            """
            buildscript {
                ${requirePropertiesFromProject()}
            }
            """
        )

        // then:
        build(*buildArguments)
    }

    private
    fun requirePropertiesFromSettings() =
        """
        ${requireNotOverriddenPropertiesFrom("settings")}
        ${requireOverriddenPropertiesFrom("settings")}
        ${requireEnvironmentPropertiesFrom("settings")}
        ${requireProjectMutatedPropertiesOriginalValuesFrom("settings")}
        """.trimIndent()

    private
    fun requirePropertiesFromProject() =
        """
        ${requireNotOverriddenPropertiesFrom("project")}
        ${requireOverriddenPropertiesFrom("project")}
        ${requireEnvironmentPropertiesFrom("project")}
        ${requireProjectExtraProperties()}
        ${requireProjectMutatedPropertiesOriginalValuesFrom("project")}
        ${requireProjectPropertiesMutation()}
        """.trimIndent()

    private
    fun requireNotOverriddenPropertiesFrom(source: String) =
        """
        ${requireProperty<String>(source, "setUserHomeProperty", """"user home value"""")}
        ${requireProperty<String>(source, "emptyUserHomeProperty", """""""")}
        ${requireProperty<String>(source, "setBuildProperty", """"build value"""")}
        ${requireProperty<String>(source, "emptyBuildProperty", """""""")}
        ${requireProperty<String>(source, "setCliProperty", """"cli value"""")}
        ${requireProperty<String>(source, "emptyCliProperty", """""""")}
        ${requireNullableProperty<String>(source, "unsetProperty", "null")}
        """.trimIndent()

    private
    fun requireOverriddenPropertiesFrom(source: String) =
        """
        ${requireProperty<String>(source, "userHomeOverriddenBuildProperty", """"user home value"""")}
        ${requireProperty<String>(source, "cliOverriddenBuildProperty", """"cli value"""")}
        ${requireProperty<String>(source, "cliOverriddenUserHomeProperty", """"cli value"""")}
        """.trimIndent()

    private
    fun requireEnvironmentPropertiesFrom(source: String) =
        """
        ${requireProperty<String>(source, "setOrgGradleProjectSystemProperty", """"system property value"""")}
        ${requireProperty<String>(source, "emptyOrgGradleProjectSystemProperty", """""""")}
        """.trimIndent()

    private
    fun requireProjectExtraProperties() =
        """
        run {
            extra["setExtraProperty"] = "extra value"
            extra["emptyExtraProperty"] = ""
            extra["unsetExtraProperty"] = null

            val setExtraProperty: String by project
            require(setExtraProperty == "extra value")

            val emptyExtraProperty: String by project
            require(emptyExtraProperty == "")

            val unsetExtraProperty: String? by project
            require(unsetExtraProperty == null)

            setProperty("setExtraProperty", "mutated")
            require(setExtraProperty == "mutated")
        }
        """.trimIndent()

    private
    fun requireProjectMutatedPropertiesOriginalValuesFrom(source: String) =
        """
        ${requireProperty<String>(source, "projectMutatedBuildProperty", """"build value"""")}
        ${requireProperty<String>(source, "projectMutatedUserHomeProperty", """"user home value"""")}
        """.trimIndent()

    private
    fun requireProjectPropertiesMutation() =
        """
        run {
            val projectMutatedBuildProperty: String by project
            require(projectMutatedBuildProperty == "build value")

            setProperty("projectMutatedBuildProperty", "mutated")
            require(projectMutatedBuildProperty == "mutated")

            val projectMutatedUserHomeProperty: String by project
            require(projectMutatedUserHomeProperty == "user home value")

            setProperty("projectMutatedUserHomeProperty", "mutated")
            require(projectMutatedUserHomeProperty == "mutated")
        }
        """.trimIndent()

    private
    inline fun <reified T : Any> requireProperty(source: String, name: String, valueRepresentation: String) =
        requireProperty(source, name, T::class.qualifiedName!!, valueRepresentation)

    private
    inline fun <reified T : Any> requireNullableProperty(source: String, name: String, valueRepresentation: String) =
        requireProperty(source, name, "${T::class.qualifiedName!!}?", valueRepresentation)

    private
    fun requireProperty(source: String, name: String, type: String, valueRepresentation: String) =
        """
        run {
            val $name: $type by $source
            require($name == $valueRepresentation) {
                ${"\"".repeat(3)}expected $name to be '$valueRepresentation' but was '${'$'}$name'${"\"".repeat(3)}
            }
        }
        """.trimIndent()
}
