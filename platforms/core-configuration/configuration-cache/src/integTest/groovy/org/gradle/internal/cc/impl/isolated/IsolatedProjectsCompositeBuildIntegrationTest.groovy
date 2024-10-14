/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.test.fixtures.file.TestFile

import java.util.function.Consumer

class IsolatedProjectsCompositeBuildIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {
    def "can build libraries composed from multiple builds"() {
        settingsFile << """
            includeBuild("libs")
        """
        file("libs/settings.gradle") << """
            include("a")
        """
        file("libs/a/build.gradle") << """
            plugins { id('java-library') }
            group = 'libs'
        """
        file("build.gradle") << """
            plugins { id('java-library') }
            dependencies { implementation 'libs:a:' }
        """

        when:
        isolatedProjectsRun(":assemble")

        then:
        fixture.assertStateStored {
            projectsConfigured(":", ":libs", ":libs:a")
        }

        when:
        isolatedProjectsRun(":assemble")

        then:
        fixture.assertStateLoaded()
    }

    def "cycles for plugin builds are prohibited"() {
        given:
        includePluginBuild(settingsFile, "plugins-a")
        includedBuild("plugins-a") { layout ->
            includePluginBuild(layout.settingsScript, "../plugins-b")
        }
        includedBuild("plugins-b") { layout ->
            includePluginBuild(layout.settingsScript, "../plugins-c")
        }
        includedBuild("plugins-c") { layout ->
            includePluginBuild(layout.settingsScript, "../plugins-a")
        }

        when:
        isolatedProjectsFails("help")

        then:
        failureDescriptionContains("Cycle detected in the included builds definition: :plugins-c -> :plugins-a -> :plugins-b -> :plugins-c")
    }

    def "transitive cycles for plugin builds are prohibited"() {
        given:
        includePluginBuild(settingsFile, "plugins-a")
        includedBuild("plugins-a") { layout ->
            includeLibraryBuild(layout.settingsScript, "../library-b")
        }
        includedBuild("library-b") { layout ->
            includeLibraryBuild(layout.settingsScript, "../library-c")
        }
        includedBuild("library-c") { layout ->
            includePluginBuild(layout.settingsScript, "../plugins-a")
        }

        when:
        isolatedProjectsFails("help")

        then:
        failureDescriptionContains("Cycle detected in the included builds definition: :library-c -> :plugins-a -> :library-b -> :library-c")
    }

    def "introduced-by-settings-plugin cycles for plugins builds are prohibited"() {
        given:
        includedBuild("settings-plugins") { layout ->
            layout.buildScript << """
                plugins {
                    id("groovy-gradle-plugin")
                }
            """
            layout.srcMainGroovy.file("my-plugin.settings.gradle") << """
                pluginManagement {
                    includeBuild("../build-logic")
                }
            """
        }
        includedBuild("build-logic") { layout ->
            includePluginBuild(layout.settingsScript, "../settings-plugins")
            layout.settingsScript << """
                plugins {
                    id("my-plugin")
                }
            """
        }
        includePluginBuild(settingsFile, "build-logic")

        when:
        isolatedProjectsFails("help")

        then:
        failureDescriptionContains("Cycle detected in the included builds definition: :build-logic -> :build-logic")
    }

    def "cycles for library builds are allowed"() {
        given:
        includeLibraryBuild(settingsFile, "library-a")
        includedBuild("library-a") { layout ->
            includeLibraryBuild(layout.settingsScript, "../library-b")
        }
        includedBuild("library-b") { layout ->
            includeLibraryBuild(layout.settingsScript, "../library-c")
        }
        includedBuild("library-c") { layout ->
            includeLibraryBuild(layout.settingsScript, "../library-a")
        }

        expect:
        isolatedProjectsRun("help")
    }

    private def includedBuild(String root, Consumer<BuildLayout> configure) {
        configure(new BuildLayout(file("$root/settings.gradle"), file("$root/build.gradle"), file("$root/src/main/groovy")))
    }

    private static def includeLibraryBuild(TestFile settingsFile, String build) {
        settingsFile << """
            includeBuild("$build")
       """
    }

    private static def includePluginBuild(TestFile settingsFile, String build) {
        settingsFile << """
            pluginManagement {
                includeBuild("$build")
            }
        """
    }

    private class BuildLayout {
        TestFile settingsScript
        TestFile buildScript
        TestFile srcMainGroovy

        BuildLayout(TestFile settingsScript, TestFile buildScript, TestFile srcMainGroovy) {
            this.settingsScript = settingsScript
            this.buildScript = buildScript
            this.srcMainGroovy = srcMainGroovy
        }
    }
}
