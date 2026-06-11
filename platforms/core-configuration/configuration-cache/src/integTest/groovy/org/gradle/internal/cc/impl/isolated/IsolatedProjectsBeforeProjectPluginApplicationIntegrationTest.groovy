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

@Issue("https://github.com/gradle/gradle/issues/36461")
class IsolatedProjectsBeforeProjectPluginApplicationIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    private static final String CONVENTION_PLUGIN_ID = "my.convention"

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
        file("build-logic/src/main/groovy/${CONVENTION_PLUGIN_ID}.gradle") << """
            plugins.apply("base")
            def projectPath = project.path
            tasks.register("verifyConvention") {
                doLast { println("convention applied to " + projectPath) }
            }
        """

        // Empty build files materialize the subproject directories so include("a") and include("b") succeed.
        file("a/build.gradle.kts") << ""
        file("b/build.gradle.kts") << ""
    }

    def "fails with hint when applying included-build plugin via beforeProject without settings plugins {} declaration"() {
        given:
        file("settings.gradle.kts") << """
            pluginManagement {
                includeBuild("build-logic")
            }
            rootProject.name = "consumer"
            include("a")
            include("b")
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
        failureCauseContains("https://docs.gradle.org/current/userguide/isolated_projects.html#sec:lifecycle_callbacks_with_included_plugin_builds")
    }

    def "applying included-build plugin via beforeProject works when declared in settings plugins {} block"() {
        given:
        file("settings.gradle.kts") << """
            pluginManagement {
                includeBuild("build-logic")
            }
            plugins {
                id("${CONVENTION_PLUGIN_ID}") apply false
            }
            rootProject.name = "consumer"
            include("a")
            include("b")
            gradle.lifecycle.beforeProject {
                apply(plugin = "${CONVENTION_PLUGIN_ID}")
            }
        """

        when:
        isolatedProjectsRun(":a:verifyConvention", ":b:verifyConvention")

        then:
        outputContains("convention applied to :a")
        outputContains("convention applied to :b")
    }

    def "applying included-build plugin via beforeProject works from a settings convention plugin"() {
        given:
        file("build-logic/src/main/groovy/my.lifecycle.settings.gradle") << """
            gradle.lifecycle.beforeProject {
                it.apply plugin: '${CONVENTION_PLUGIN_ID}'
            }
        """
        file("settings.gradle.kts") << """
            pluginManagement {
                includeBuild("build-logic")
            }
            plugins {
                id("my.lifecycle")
            }
            rootProject.name = "consumer"
            include("a")
            include("b")
        """

        when:
        isolatedProjectsRun(":a:verifyConvention", ":b:verifyConvention")

        then:
        outputContains("convention applied to :a")
        outputContains("convention applied to :b")
    }

    def "applying included-build plugin via beforeProject reuses configuration cache"() {
        given:
        file("settings.gradle.kts") << """
            pluginManagement {
                includeBuild("build-logic")
            }
            plugins {
                id("${CONVENTION_PLUGIN_ID}") apply false
            }
            rootProject.name = "consumer"
            include("a")
            include("b")
            gradle.lifecycle.beforeProject {
                apply(plugin = "${CONVENTION_PLUGIN_ID}")
            }
        """

        when:
        isolatedProjectsRun(":a:verifyConvention", ":b:verifyConvention")

        then:
        fixture.assertStateStored {
            projectsConfigured(":build-logic", ":", ":a", ":b")
        }
        outputContains("convention applied to :a")
        outputContains("convention applied to :b")

        when:
        isolatedProjectsRun(":a:verifyConvention", ":b:verifyConvention")

        then:
        fixture.assertStateLoaded()
        outputContains("convention applied to :a")
        outputContains("convention applied to :b")
    }
}
