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

    protected
    val pluginManagementBlock by lazy {
        """
            pluginManagement {
                $repositoriesBlock
                resolutionStrategy {
                    eachPlugin {
                        if (requested.id.namespace == "org.gradle.kotlin") {
                            useVersion("$futurePluginsVersion")
                        }
                    }
                }
            }
        """
    }

    private
    val repositoriesBlock by lazy {
        """
            repositories {
                maven { url = uri("$testRepositoryPath") }
                gradlePluginPortal()
            }
        """
    }

    private
    val futurePluginsVersion by lazy {
        loadTestProperties()["version"]!!
    }

    private
    val testRepositoryPath: String
        get() = normaliseFileSeparators(absolutePathOf("build/repository"))

    @Before
    fun setUpTestPluginRepository() {
        withSettings(pluginManagementBlock)
    }

    private
    fun loadTestProperties(): Properties =
        javaClass.getResourceAsStream("/test.properties").use {
            Properties().apply { load(it) }
        }

    protected
    fun buildWithPlugin(vararg arguments: String) =
        build(*arguments)

    private
    fun absolutePathOf(path: String) =
        File(path).absolutePath
}
