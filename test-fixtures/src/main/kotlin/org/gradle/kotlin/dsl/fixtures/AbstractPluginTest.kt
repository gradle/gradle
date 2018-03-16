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
    val testPluginRepositorySettings by lazy {
        val testRepository = normaliseFileSeparators(absolutePathOf("build/repository"))
        val futureVersion = loadTestProperties()["version"]
        """
            pluginManagement {
                repositories {
                    maven { url = uri("$testRepository") }
                    gradlePluginPortal()
                }
                resolutionStrategy {
                    eachPlugin {
                        if (requested.id.namespace == "org.gradle.kotlin") {
                            useVersion("$futureVersion")
                        }
                    }
                }
            }
        """
    }

    @Before
    fun setUpTestPluginRepository() {
        withSettings(testPluginRepositorySettings)
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
