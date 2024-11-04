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

class IsolatedProjectsCompositeBuildIntegrationTest extends AbstractIsolatedProjectsCompositeBuildIntegrationTest {
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
        applyPlugins(buildFile, "plugin-a")

        includedBuild("plugins-a") {
            includePluginBuild(settingsScript, "../plugins-b")
            applyPlugins(buildScript, "groovy-gradle-plugin", "plugin-b")
            srcMainGroovy.file("plugin-a.gradle") << ""
        }

        includedBuild("plugins-b") {
            includePluginBuild(settingsScript, "../plugins-c")
            applyPlugins(buildScript, "groovy-gradle-plugin", "plugin-c")
            srcMainGroovy.file("plugin-b.gradle") << ""
        }

        includedBuild("plugins-c") {
            includePluginBuild(settingsScript, "../plugins-a")
            applyPlugins(buildScript, "groovy-gradle-plugin", "plugin-a")
            srcMainGroovy.file("plugin-c.gradle") << ""
        }

        when:
        isolatedProjectsFails("help")

        then:
        failureDescriptionContains("A cycle has been detected in the definition of plugin builds: :plugins-a -> :plugins-b -> :plugins-c -> :plugins-a.")
    }

    def "transitive cycles(start is a plugin) for plugin builds are prohibited"() {
        given:
        includePluginBuild(settingsFile, "plugins-a")
        applyPlugins(buildFile, "plugin-a")

        includedBuild("plugins-a") {
            includeLibraryBuild(settingsScript, "../library-b")
            srcMainGroovy.file("plugin-a.gradle") << ""
        }

        includedBuild("library-b") {
            includeLibraryBuild(settingsScript, "../library-c")
        }

        includedBuild("library-c") {
            includePluginBuild(settingsScript, "../plugins-a")
            applyPlugins(buildScript, "plugin-a")
        }

        when:
        isolatedProjectsFails("help")

        then:
        failureDescriptionContains("A cycle has been detected in the definition of plugin builds: :plugins-a -> :library-b -> :library-c -> :plugins-a.")
    }

    def "transitive cycles(start is a library) for plugin builds are prohibited"() {
        given:
        includeLibraryBuild(settingsFile, "library-a")

        includedBuild("library-a") {
            includeLibraryBuild(settingsScript, "../library-b")
        }

        includedBuild("library-b") {
            includePluginBuild(settingsScript, "../plugins-a")
            applyPlugins(buildScript, "plugin-a")
        }

        includedBuild("library-c") {
            includeLibraryBuild(settingsScript, "../library-b")
        }

        includedBuild("plugins-a") {
            includeLibraryBuild(settingsScript, "../library-c")
            applyPlugins(buildScript, "groovy-gradle-plugin")
            srcMainGroovy.file("plugin-a.gradle") << ""
        }

        includedBuild("library-c") {
            includeLibraryBuild(settingsScript, "../library-b")
        }

        when:
        isolatedProjectsFails("help")

        then:
        failureDescriptionContains("A cycle has been detected in the definition of plugin builds: :plugins-a -> :library-c -> :library-b -> :plugins-a.")
    }

    def "introduced-by-settings-plugin cycles for plugins builds are prohibited"() {
        given:
        includePluginBuild(settingsFile, "build-logic")
        applyPlugins(buildFile, "plugin-a")

        includedBuild("settings-plugins") {
            applyPlugins(buildScript, "groovy-gradle-plugin")
            srcMainGroovy.file("my-plugin.settings.gradle") << """
                pluginManagement {
                    includeBuild("../build-logic")
                }
            """
        }

        includedBuild("build-logic") {
            includePluginBuild(settingsScript, "../settings-plugins")
            applyPlugins(settingsScript, "my-plugin")
            applyPlugins(buildScript, "groovy-gradle-plugin")
            srcMainGroovy.file("plugin-a.gradle") << ""
        }

        when:
        isolatedProjectsFails("help")

        then:
        failureDescriptionContains("A cycle has been detected in the definition of plugin builds: :build-logic -> :build-logic.")
    }

    def "cycles for library builds are allowed"() {
        given:
        includeLibraryBuild(settingsFile, "library-a")
        includedBuild("library-a") {
            includeLibraryBuild(settingsScript, "../library-b")
        }
        includedBuild("library-b") {
            includeLibraryBuild(settingsScript, "../library-c")
        }
        includedBuild("library-c") {
            includeLibraryBuild(settingsScript, "../library-a")
        }

        expect:
        isolatedProjectsRun("help")
    }

    def "plugins from included build are safe to be requested concurrently"() {
        given:
        includedBuild("plugins") {
            applyPlugins(buildScript, "groovy-gradle-plugin")
            srcMainGroovy.file("foo.gradle") << """println("Foo plugin applied to \$project")"""
            srcMainGroovy.file("bar.gradle") << """println("Bar plugin applied to \$project")"""
        }

        includePluginBuild(settingsFile, "plugins")
        settingsFile """
            include(":a")
            include(":b")
        """
        applyPlugins(file("a/build.gradle"), "foo")
        applyPlugins(file("b/build.gradle"), "bar")

        when:
        isolatedProjectsRun("help")

        then:
        outputContains("Foo plugin applied to project ':a'")
        outputContains("Bar plugin applied to project ':b'")
    }

    def "substitutions from library builds are safe to be registered concurrently"() {
        includedBuild("plugins-a") {
            includeLibraryBuild(settingsScript, "../shared-library")
            applyPlugins(buildScript, "groovy-gradle-plugin")
            srcMainGroovy.file("plugin-a.gradle") << ""
            buildScript << """
                dependencies {
                    implementation("com.example:shared-library")
                }
            """
        }

        includedBuild("plugins-b") {
            includeLibraryBuild(settingsScript, "../shared-library")
            applyPlugins(buildScript, "groovy-gradle-plugin")
            srcMainGroovy.file("plugin-b.gradle") << ""
            buildScript << """
                dependencies {
                    implementation("com.example:shared-library")
                }
            """
        }

        includedBuild("shared-library") {
            applyPlugins(buildScript, "java-library")
            buildScript << """
                group = "com.example"
                version = "1.0"
            """
        }

        includePluginBuild(settingsFile, "plugins-a", "plugins-b")
        settingsFile << """
                include(":a")
                include(":b")
        """

        applyPlugins(file("a/build.gradle"), "plugin-a")
        applyPlugins(file("b/build.gradle"), "plugin-b")

        expect:
        isolatedProjectsRun("help")
    }

    def "substitutions from library builds are safe to be registered from cycled definition"() {
        given:
        includedBuild("library-a") {
            includeLibraryBuild(settingsScript, "../library-b")
            applyPlugins(buildScript, "java-library")
            buildScript << """
                group = "com.example"
                version = "1.0"

                dependencies {
                    implementation("com.example:library-b")
                }
            """
        }

        includedBuild("library-b") {
            includeLibraryBuild(settingsScript, "../library-a")
            applyPlugins(buildScript, "java-library")
            buildScript << """
                group = "com.example"
                version = "1.0"

                dependencies {
                    implementation("com.example:library-a")
                }
            """
        }

        includeLibraryBuild(settingsFile, "library-a")
        includeLibraryBuild(settingsFile, "library-b")
        buildFile """
            plugins {
                id "java"
            }
            dependencies {
                implementation("com.example:library-a")
                implementation("com.example:library-b")
            }
        """

        expect:
        isolatedProjectsRun("help")
    }
}
