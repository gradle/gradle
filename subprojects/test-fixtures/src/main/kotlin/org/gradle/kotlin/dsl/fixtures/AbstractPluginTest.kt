package org.gradle.kotlin.dsl.fixtures

import org.gradle.util.TextUtil.normaliseFileSeparators

import org.junit.Before

import java.io.File
import java.util.*


/**
 * Base class for Gradle plugins tests.
 * You must apply the `kotlin-dsl-plugin-bundle` plugin for this to work.
 */
open class AbstractPluginTest : AbstractIntegrationTest() {

    @Before
    fun setUpTestPluginRepository() {
        withSettings(pluginManagementBlock)
    }

    override val defaultSettingsScript: String
        get() = pluginManagementBlock

    protected
    val pluginManagementBlock by lazy {
        """
            pluginManagement {
                $pluginRepositoriesBlock
                $resolutionStrategyBlock
            }
        """
    }

    protected
    val pluginRepositoriesBlock by lazy {
        """
            repositories {
                $testRepositories
                gradlePluginPortal()
            }
        """
    }

    private
    val testRepositories: String
        get() = testRepositoryPaths.joinLines {
            """
                maven(url = "$it")
            """
        }

    private
    val resolutionStrategyBlock
        get() = """
            resolutionStrategy {
                eachPlugin {
                    $futurePluginRules
                }
            }
        """

    private
    val futurePluginRules: String?
        get() = futurePluginVersions?.entries?.joinLines { (id, version) ->
            """
                if (requested.id.id == "$id") {
                    useVersion("$version")
                }
            """
        }

    private
    val futurePluginVersions by lazy {
        loadPropertiesFromResource("/future-plugin-versions.properties")
    }

    private
    fun loadPropertiesFromResource(name: String): Properties? =
        javaClass.getResourceAsStream(name)?.use {
            Properties().apply { load(it) }
        }

    protected
    open val testRepositoryPaths: List<String>
        get() = normalisedPathsOf("build/repository")

    protected
    fun buildWithPlugin(vararg arguments: String) =
        build(*arguments)

    protected
    fun normalisedPathsOf(vararg paths: String) =
        paths.map(::normalisedPathOf)

    protected
    fun normalisedPathOf(relativePath: String) =
        normaliseFileSeparators(absolutePathOf(relativePath))

    private
    fun absolutePathOf(path: String) =
        File(path).absolutePath
}
