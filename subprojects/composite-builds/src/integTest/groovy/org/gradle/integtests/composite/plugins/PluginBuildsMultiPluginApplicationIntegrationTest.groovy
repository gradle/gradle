/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.integtests.composite.plugins

import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/29652")
class PluginBuildsMultiPluginApplicationIntegrationTest extends AbstractPluginBuildIntegrationTest {

    def "plugin from included plugin build can be applied to several projects requesting version #requestedVersion while the build produces #producedVersion"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        pluginBuild.buildFile << """
            version = "$producedVersion"
        """
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
            include("child")
        """
        def request = requestedVersion == null
            ? """id("${pluginBuild.projectPluginId}")"""
            : """id("${pluginBuild.projectPluginId}") version "$requestedVersion" """
        buildFile << """
            plugins {
                $request
            }
        """
        file("child/build.gradle") << """
            plugins {
                $request
            }
        """

        when:
        succeeds("help")

        then:
        pluginBuild.assertProjectPluginApplied()

        where:
        requestedVersion | producedVersion
        null             | "1.1"
        "1.0"            | "1.1"
        "1.0"            | "1.0"
        "1.0"            | "unspecified"
    }

    def "plugin from a regular included build can be applied to several projects with a version that differs from the produced one"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        pluginBuild.buildFile << """
            version = "1.1"
        """
        settingsFile << """
            includeBuild("${pluginBuild.buildName}")
            include("child")
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}") version "1.0"
            }
        """
        file("child/build.gradle") << """
            plugins {
                id("${pluginBuild.projectPluginId}") version "1.0"
            }
        """

        when:
        succeeds("help")

        then:
        pluginBuild.assertProjectPluginApplied()
    }

    def "plugin from included plugin build can be applied at several levels of project nesting"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        pluginBuild.buildFile << """
            version = "1.1"
        """
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
            include("a")
            include("a:b")
        """
        [buildFile, file("a/build.gradle"), file("a/b/build.gradle")].each {
            it << """
                plugins {
                    id("${pluginBuild.projectPluginId}") version "1.0"
                }
            """
        }

        when:
        succeeds("help")

        then:
        pluginBuild.assertProjectPluginApplied()
    }

    def "projects can request different versions of the same plugin from an included plugin build"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        pluginBuild.buildFile << """
            version = "1.1"
        """
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
            include("child")
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}") version "1.0"
            }
        """
        file("child/build.gradle") << """
            plugins {
                id("${pluginBuild.projectPluginId}") version "2.0"
            }
        """

        when:
        succeeds("help")

        then:
        pluginBuild.assertProjectPluginApplied()
    }

    def "plugin from included plugin build can be requested with a version in one project and without in another"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        pluginBuild.buildFile << """
            version = "1.1"
        """
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
            include("child")
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")${rootVersion ? " version \"$rootVersion\"" : ""}
            }
        """
        file("child/build.gradle") << """
            plugins {
                id("${pluginBuild.projectPluginId}")${childVersion ? " version \"$childVersion\"" : ""}
            }
        """

        when:
        succeeds("help")

        then:
        pluginBuild.assertProjectPluginApplied()

        where:
        rootVersion | childVersion
        "1.0"       | null
        null        | "1.0"
    }

    def "plugin resolved from a repository is still rejected when projects request different versions"() {
        given:
        def repoDeclaration = """
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }
        """
        publishProjectPlugin("com.example.published", repoDeclaration)
        settingsFile << """
            pluginManagement {
                $repoDeclaration
            }
            include("child")
        """
        buildFile << """
            plugins {
                id("com.example.published") version "1.0"
            }
        """
        file("child/build.gradle") << """
            plugins {
                id("com.example.published") version "2.0"
            }
        """

        expect:
        fails("help")
        failureHasCause("The request for this plugin could not be satisfied because the plugin is already on the classpath with a different version (1.0).")
    }

    @ToBeImplemented("A plugin id that has no tracked version is not recognised as local, even when a sibling plugin from the same included build put it on the classpath")
    def "settings plugin and project plugin from the same included plugin build can both be requested with a version"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        pluginBuild.buildFile << """
            version = "1.1"
        """
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}") version "1.0"
            }
            include("child")
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}") version "1.0"
            }
        """
        file("child/build.gradle") << """
            plugins {
                id("${pluginBuild.projectPluginId}") version "1.0"
            }
        """

        expect:
        // Should be:
        //   succeeds("help")
        //   pluginBuild.assertSettingsPluginApplied()
        //   pluginBuild.assertProjectPluginApplied()
        // The settings plugin puts the whole included build on the classpath of the root project's parent scope,
        // but only its own plugin id is recorded in the PluginVersionTracker. The project plugin id is therefore
        // seen as "on the classpath with an unknown version".
        fails("help")
        failureHasCause("The request for this plugin could not be satisfied because the plugin is already on the classpath with an unknown version, so compatibility cannot be checked.")
    }
}
