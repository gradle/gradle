package org.gradle.kotlin.dsl.fixtures

import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext

import org.junit.Before

import java.util.Properties


/**
 * Base class for Gradle plugins tests.
 * You must apply the `kotlin-dsl-plugin-bundle` plugin for this to work.
 */
open class AbstractPluginTest : AbstractKotlinIntegrationTest() {

    @Before
    fun setupKotlinDslPluginsRepositories() {
        val setupScript = file(".setupKotlinDslPlugins/setup-kotlin-dsl-plugins.init.gradle")
        setupScript.parentFile.mkdirs()
        setupScript.writeText(
            """
            beforeSettings { settings ->
                settings.pluginManagement {
                    repositories {
                        $testRepositories
                        gradlePluginPortal()
                    }
                    resolutionStrategy {
                        eachPlugin {
                            $futurePluginRules
                        }
                    }
                }
            }
            """
        )
        executer.beforeExecute {
            usingInitScript(setupScript)
        }
    }

    @Before
    fun setUpDefaultSettings() {
        withDefaultSettings()
    }

    private
    val testRepositories: String
        get() = testRepositoryPaths.joinLines {
            """
                maven { url = uri("$it") }
            """
        }

    private
    val futurePluginRules: String
        get() = futurePluginVersions.entries.joinLines { (id, version) ->
            """
                if (requested.id.id == "$id") {
                    useVersion("$version")
                }
            """
        }

    protected
    val futurePluginVersions by lazy {
        loadPropertiesFromResource("/future-plugin-versions.properties")
            ?: throw IllegalStateException("/future-plugin-versions.properties resource not found.")
    }

    private
    fun loadPropertiesFromResource(name: String): Properties? =
        javaClass.getResourceAsStream(name)?.use {
            Properties().apply { load(it) }
        }

    private
    val testRepositoryPaths: List<String>
        get() = IntegrationTestBuildContext().localRepository?.let { listOf(it.normalisedPath) } ?: emptyList()
}
