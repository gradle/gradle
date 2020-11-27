/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.composite

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class CompositeBuildLogicBuildsIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def "early included build logic build can contribute settings plugins"() {
        given:
        buildLogicBuild('build-logic')
        settingsFile << """
            pluginManagement {
                includeBuildEarly('build-logic')
            }
            plugins {
                id("build-logic.settings-plugin")
            }
        """

        when:
        succeeds()

        then:
        outputContains("build-logic settings plugin applied")
    }

    def "early included build logic build can contribute project plugins"() {
        given:
        buildLogicBuild('build-logic')
        settingsFile << """
            pluginManagement {
                includeBuildEarly('build-logic')
            }
        """
        buildFile << """
            plugins {
                id("build-logic.project-plugin")
            }
        """

        when:
        succeeds()

        then:
        outputContains("build-logic project plugin applied")
    }

    def "early included build logic build can contribute both settings and project plugins"() {
        given:
        buildLogicBuild('build-logic')
        settingsFile << """
            pluginManagement {
                includeBuildEarly('build-logic')
            }
            plugins {
                id("build-logic.settings-plugin")
            }
        """
        buildFile << """
            plugins {
                id("build-logic.project-plugin")
            }
        """

        when:
        succeeds()

        then:
        outputContains("build-logic settings plugin applied")
        outputContains("build-logic project plugin applied")
    }

    def "included build logic builds can not contribute settings plugins"() {
        given:
        buildLogicBuild('build-logic')
        settingsFile << """
            pluginManagement {
                includeBuild('build-logic')
            }
            plugins {
                id("build-logic.settings-plugin")
            }
        """

        when:
        fails()

        then:
        failureDescriptionContains("Plugin [id: 'build-logic.settings-plugin'] was not found in any of the following sources:")
    }

    def "included build logic builds can contribute project plugins"() {
        given:
        buildLogicBuild('build-logic')
        settingsFile << """
            pluginManagement {
                includeBuild('build-logic')
            }
        """
        buildFile << """
            plugins {
                id("build-logic.project-plugin")
            }
        """

        when:
        succeeds()

        then:
        outputContains("build-logic project plugin applied")
    }

    def "regular included build can not contribute settings plugins"() {
        given:
        buildLogicBuild('build-logic')
        settingsFile << """
            plugins {
                id("build-logic.settings-plugin")
            }
            includeBuild('build-logic')
        """

        when:
        fails()

        then:
        failureDescriptionContains("Plugin [id: 'build-logic.settings-plugin'] was not found in any of the following sources:")
    }

    @NotYetImplemented
    def "regular included builds contributing project plugins is deprecated"() {
        given:
        buildLogicBuild('build-logic')
        settingsFile << """
            includeBuild('build-logic')
        """
        buildFile << """
            plugins {
                id("build-logic.project-plugin")
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Including builds that contribute Gradle plugins outside of pluginManagement {} block in settings file has been deprecated. This is scheduled to be removed in Gradle 8.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#included_builds_contributing_plugins")
        succeeds()

        then:
        outputContains("build-logic project plugin applied")
    }

    def "can nest included build logic builds"() {
        when:
        buildLogicBuild('logic-1')
        buildLogicBuild('logic-2')
        settingsFile << """
            pluginManagement {
                includeBuild('logic-1')
            }
        """
        file('logic-1/settings.gradle') << """
            includeBuild('../logic-2')
        """

        then:
        succeeds()
    }

    @NotYetImplemented
    def "can nest early included build logic builds"() {
        when:
        buildLogicBuild('logic-1')
        buildLogicBuild('logic-2')
        settingsFile << """
            pluginManagement {
                includeBuildEarly('logic-1')
            }
        """
        file('logic-1/settings.gradle') << """
            includeBuild('../logic-2')
        """

        then:
        succeeds()
    }

    @ToBeFixedForConfigurationCache(because = "groovy precompiled scripts")
    def "included build logic build is not visible as library component"() {
        given:
        buildLogicAndProductionLogicBuild('included-build')
        settingsFile << """
            pluginManagement {
                includeBuild('included-build')
            }
        """

        when:
        buildFile << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation("com.example:included-build")
            }
        """
        file("src/main/java/Foo.java") << """
            class Foo { Bar newBar() { return new Bar(); }}
        """

        then:
        fails("build")
        failureDescriptionContains("Could not determine the dependencies of task ':compileJava'.")
        failureCauseContains("Cannot resolve external dependency com.example:included-build")
    }

    @ToBeFixedForConfigurationCache(because = "groovy precompiled scripts")
    def "early included build logic build is not visible as library component"() {
        given:
        buildLogicAndProductionLogicBuild('included-build')
        settingsFile << """
            pluginManagement {
                includeBuildEarly('included-build')
            }
        """

        when:
        buildFile << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation("com.example:included-build")
            }
        """
        file("src/main/java/Foo.java") << """
            class Foo { Bar newBar() { return new Bar(); }}
        """

        then:
        fails("build")
        failureDescriptionContains("Could not determine the dependencies of task ':compileJava'.")
        failureCauseContains("Cannot resolve external dependency com.example:included-build")
    }

    def "a build can be included both as a build logic build and as regular build and can contribute both plugins and library components"() {
        given:
        buildLogicAndProductionLogicBuild('included-build')
        settingsFile << """
            pluginManagement {
                includeBuild('included-build')
            }
            includeBuild('included-build')
        """

        when:
        buildFile << """
            plugins {
                id("java-library")
                id("included-build.project-plugin")
            }
            dependencies {
                implementation("com.example:included-build")
            }
        """
        file("src/main/java/Foo.java") << """
            class Foo { Bar newBar() { return new Bar(); }}
        """

        then:
        succeeds("build")
        assertTaskExecuted(':included-build', ':compileJava')
        assertTaskExecuted(':', ':compileJava')
        outputContains('included-build project plugin applied')
    }

    def "a build can be included both as an early build logic build and as regular build and can contribute both settings plugins and library components"() {
        given:
        buildLogicAndProductionLogicBuild('included-build')
        settingsFile << """
            pluginManagement {
                includeBuildEarly('included-build')
            }
            plugins {
                id("included-build.settings-plugin")
            }
            includeBuild('included-build')
        """

        when:
        buildFile << """
            plugins {
                id("java-library")
                id("included-build.project-plugin")
            }
            dependencies {
                implementation("com.example:included-build")
            }
        """
        file("src/main/java/Foo.java") << """
            class Foo { Bar newBar() { return new Bar(); }}
        """

        then:
        succeeds("build")
        assertTaskExecuted(':included-build', ':compileJava')
        assertTaskExecuted(':', ':compileJava')
        outputContains('included-build settings plugin applied')
        outputContains('included-build project plugin applied')
    }

    @NotYetImplemented
    def "build logic builds included in one build can not see each other's plugins"() {
        given:
        buildLogicBuild('logic-1')
        buildLogicBuild('logic-2')
        settingsFile << """
            pluginManagement {
                includeBuild('logic-1')
                includeBuild('logic-2')
            }
        """

        when:
        buildFile << """
            plugins {
                id("logic-1.project-plugin")
                id("logic-2.project-plugin")
            }
        """

        then:
        succeeds()
        outputContains("logic-1 project plugin applied")
        outputContains("logic-2 project plugin applied")

        when:
        file("logic-1/build.gradle").setText("""
            plugins {
                id("groovy-gradle-plugin")
                id("logic-2.project-plugin")
            }
        """)
        file("logic-2/build.gradle").setText("""
            plugins {
                id("groovy-gradle-plugin")
            }
        """)

        then:
        fails()
        failureDescriptionContains("Plugin [id: 'logic-2.project-plugin'] was not found in any of the following sources:")

        when:
        file("logic-1/build.gradle").setText("""
            plugins {
                id("groovy-gradle-plugin")
            }
        """)
        file("logic-2/build.gradle").setText("""
            plugins {
                id("groovy-gradle-plugin")
                id("logic-1.project-plugin")
            }
        """)

        then:
        fails()
        failureDescriptionContains("Plugin [id: 'logic-1.project-plugin'] was not found in any of the following sources:")
    }

    @NotYetImplemented
    def "nested included build logic build can contribute build logic to including included build"() {
        given:
        buildLogicBuild("logic-1")
        buildLogicBuild("logic-2")
        settingsFile << """
            pluginManagement {
                includeBuild("logic-2")
            }
        """
        file("logic-2/settings.gradle").setText("""
            pluginManagement {
                includeBuild("../logic-1")
            }
            rootProject.name = "logic-2"
        """)

        when:
        buildFile << """
            plugins {
                id("logic-2.project-plugin")
            }
        """
        file("logic-2/build.gradle").setText("""
            plugins {
                id("groovy-gradle-plugin")
                id("logic-1.project-plugin")
            }
        """)

        then:
        succeeds()
        outputContains("logic-1 project plugin applied")
        outputContains("logic-2 project plugin applied")
    }

    @NotYetImplemented
    def "included build logic builds are not visible transitively"() {
        given:
        buildLogicBuild("logic-1")
        buildLogicBuild("logic-2")
        settingsFile << """
            pluginManagement {
                includeBuild("logic-2")
            }
        """
        file("logic-2/settings.gradle").setText("""
            pluginManagement {
                includeBuild("../logic-1")
            }
            rootProject.name = "logic-2"
        """)

        when:
        buildFile << """
            plugins {
                id("logic-1.project-plugin")
                id("logic-2.project-plugin")
            }
        """

        then:
        fails()
        failureDescriptionContains("Plugin [id: 'logic-1.project-plugin'] was not found in any of the following sources:")
    }

    private void buildLogicBuild(String buildName) {
        file("$buildName/settings.gradle") << """
            rootProject.name = "$buildName"
        """
        file("$buildName/build.gradle") << """
            plugins {
                id("groovy-gradle-plugin")
            }
        """
        file("$buildName/src/main/groovy/${buildName}.project-plugin.gradle") << """
            println('$buildName project plugin applied')
        """
        file("$buildName/src/main/groovy/${buildName}.settings-plugin.settings.gradle") << """
            println('$buildName settings plugin applied')
        """
    }

    private void buildLogicAndProductionLogicBuild(String buildName) {
        buildLogicBuild(buildName)

        file("$buildName/build.gradle").setText("""
            plugins {
                id("groovy-gradle-plugin")
                id("java-library")
            }

            group = "com.example"
            version = "1.0"
        """)
        file("$buildName/src/main/java/Bar.java") << """
            public class Bar {}
        """
    }
}
