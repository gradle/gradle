/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.cc.impl.isolated

import spock.lang.Issue

import static org.gradle.integtests.fixtures.KotlinDslTestUtil.getKotlinDslBuildSrcConfig

@Issue("https://github.com/gradle/gradle/issues/36461")
class IsolatedProjectsBeforeProjectPluginApplicationIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    private static final String CONVENTION_PLUGIN_ID = "my.convention"
    public static final String CONVENTION_PLUGIN_SRC = """
            plugins.apply("base")
            def projectPath = project.path
            tasks.register("verifyConvention") {
                doLast { println("convention applied to [" + projectPath + "]") }
            }
        """

    def setup() {
        // The strict-mode classloader-scope check is a developer-only check that masks the
        // user-visible "Plugin not found." failure. Disable it so the test reproduces what
        // end users see on stock Gradle distributions.
        executer.withEagerClassLoaderCreationCheckDisabled()

        // build-logic is the included plugin build that publishes my.convention.
        file("build-logic/settings.gradle.kts") << """
            rootProject.name = "build-logic"
        """
        file("build-logic/build.gradle.kts") << """
            plugins { id("groovy-gradle-plugin") }
        """
        file("build-logic/src/main/groovy/${CONVENTION_PLUGIN_ID}.gradle") << CONVENTION_PLUGIN_SRC

        // Empty build file materializes the subproject directory so include("sub") succeeds.
        file("sub/build.gradle.kts") << ""

        // Consumer settings shared by all scenarios. Each test appends its own
        // plugins {} / lifecycle declarations afterwards; imperative statements such as
        // include(...) are allowed to precede a later plugins {} block.
        settingsKotlinFile << """
            pluginManagement {
                includeBuild("build-logic")
            }
            rootProject.name = "consumer"
            include("sub")
        """
    }

    def "fails with hint when applying included-build plugin via beforeProject without settings plugins {} declaration"() {
        given:
        settingsKotlinFile << """
            gradle.lifecycle.beforeProject {
                apply(plugin = "${CONVENTION_PLUGIN_ID}")
            }
        """

        when:
        isolatedProjectsFails("help")

        then:
        failureCauseContains("Plugin with id '${CONVENTION_PLUGIN_ID}' not found.")
        failureCauseContains("If this plugin is provided by a build registered via `pluginManagement.includeBuild(...)`")
        failureCauseContains("settings convention plugin")
        failureCauseContains("plugins { id(\"${CONVENTION_PLUGIN_ID}\") apply false }")
        failureCauseContains("userguide/isolated_projects.html#sec:lifecycle_callbacks_with_included_plugin_builds")
    }

    def "applying buildSrc plugin via beforeProject works without any settings declaration"() {
        given:
        file("buildSrc/build.gradle.kts") << """
            plugins { id("groovy-gradle-plugin") }
        """
        file("buildSrc/src/main/groovy/${CONVENTION_PLUGIN_ID}.gradle") << CONVENTION_PLUGIN_SRC
        // Unlike pluginManagement.includeBuild, buildSrc is built eagerly and its classpath is
        // exported to all projects, so the callback can apply the plugin with no includeBuild and
        // no plugins {} declaration. Override the shared settings to drop the unused included build.
        settingsKotlinFile.text = """
            rootProject.name = "consumer"
            include("sub")
            gradle.lifecycle.beforeProject {
                apply(plugin = "${CONVENTION_PLUGIN_ID}")
            }
        """

        when:
        isolatedProjectsRun("verifyConvention")

        then:
        outputContains("convention applied to [:]")
        outputContains("convention applied to [:sub]")
    }

    def "applying included-build plugin via beforeProject works when declared in settings plugins {} block"() {
        given:
        settingsKotlinFile << """
            plugins {
                id("${CONVENTION_PLUGIN_ID}") apply false
            }
            gradle.lifecycle.beforeProject {
                apply(plugin = "${CONVENTION_PLUGIN_ID}")
            }
        """

        when:
        isolatedProjectsRun("verifyConvention")

        then:
        outputContains("convention applied to [:]")
        outputContains("convention applied to [:sub]")
    }

    def "applying included-build plugin via beforeProject works from a settings convention plugin"() {
        given:
        file("build-logic/src/main/groovy/my.lifecycle.settings.gradle") << """
            gradle.lifecycle.beforeProject {
                it.apply plugin: '${CONVENTION_PLUGIN_ID}'
            }
        """
        settingsKotlinFile << """
            plugins {
                id("my.lifecycle")
            }
        """

        when:
        isolatedProjectsRun("verifyConvention")

        then:
        outputContains("convention applied to [:]")
        outputContains("convention applied to [:sub]")
    }

    def "applying included-build plugin via beforeProject works from a Kotlin settings convention plugin"() {
        given:
        // build-logic must compile a Kotlin precompiled settings plugin alongside the Groovy
        // convention plugin from setup, so it applies both groovy-gradle-plugin and kotlin-dsl.
        file("build-logic/build.gradle.kts").text = """
            plugins {
                id("groovy-gradle-plugin")
                `kotlin-dsl`
            }
            $kotlinDslBuildSrcConfig
        """
        file("build-logic/src/main/kotlin/my.lifecycle.settings.gradle.kts") << """
            gradle.lifecycle.beforeProject {
                apply(plugin = "${CONVENTION_PLUGIN_ID}")
            }
        """
        settingsKotlinFile << """
            plugins {
                id("my.lifecycle")
            }
        """

        when:
        isolatedProjectsRun("verifyConvention")

        then:
        outputContains("convention applied to [:]")
        outputContains("convention applied to [:sub]")
    }

    def "applying included-build plugin via beforeProject reuses configuration cache"() {
        given:
        settingsKotlinFile << """
            plugins {
                id("${CONVENTION_PLUGIN_ID}") apply false
            }
            gradle.lifecycle.beforeProject {
                apply(plugin = "${CONVENTION_PLUGIN_ID}")
            }
        """

        when:
        isolatedProjectsRun("verifyConvention")

        then:
        fixture.assertStateStored {
            projectsConfigured(":build-logic", ":", ":sub")
        }
        outputContains("convention applied to [:]")
        outputContains("convention applied to [:sub]")

        when:
        isolatedProjectsRun("verifyConvention")

        then:
        fixture.assertStateLoaded()
        outputContains("convention applied to [:]")
        outputContains("convention applied to [:sub]")
    }
}
