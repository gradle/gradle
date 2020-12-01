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

class CompositeBuildLogicBuildsIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def "included build logic builds can contribute settings plugins"() {
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
        succeeds()

        then:
        outputContains("build-logic settings plugin applied")
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

    def "included build logic build can contribute both settings and project plugins"() {
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

    def "settings plugin from included build is used over published plugin when no version is specified"() {
        given:
        def repoDeclaration = """
            repositories {
                maven {
                    url("${mavenRepo.uri}")
                }
            }
        """
        def pluginId = "build-logic.settings-plugin"
        publishSettingsPlugin(pluginId, repoDeclaration)
        buildLogicBuild('build-logic')

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild('build-logic')
            }
            plugins {
                id("$pluginId")
            }
        """

        then:
        succeeds()
        outputContains("build-logic settings plugin applied")
    }

    def "published settings plugin is used over included build plugin when version is specified"() {
        given:
        def repoDeclaration = """
            repositories {
                maven {
                    url("${mavenRepo.uri}")
                }
            }
        """
        def pluginId = "build-logic.settings-plugin"
        publishSettingsPlugin(pluginId, repoDeclaration)
        buildLogicBuild('build-logic')

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild('build-logic')
            }
            plugins {
                id("$pluginId") version "1.0"
            }
        """

        then:
        succeeds()
        outputContains("$pluginId from repository applied")
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

    // Emit the deprecation warning in CompositeBuildPluginResolverContributor once we settle on and publicize the new API for build logic builds
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

    def "a build can be included both as a build logic build and as regular build and can contribute both settings plugins and library components"() {
        given:
        buildLogicAndProductionLogicBuild('included-build')
        settingsFile << """
            pluginManagement {
                includeBuild('included-build')
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

    private void publishSettingsPlugin(String pluginId, String repoDeclaration) {
        file("plugin/src/main/java/PublishedSettingsPlugin.java") << """
            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;

            public class PublishedSettingsPlugin implements Plugin<Settings> {
                @Override
                public void apply(Settings settings) {
                    System.out.println("${pluginId} from repository applied");
                }
            }
        """
        file("plugin/build.gradle") << """
            plugins {
                id("java-gradle-plugin")
                id("maven-publish")
            }
            group = "com.example"
            version = "1.0"
            publishing {
                $repoDeclaration
            }
            gradlePlugin {
                plugins {
                    publishedPlugin {
                        id = '${pluginId}'
                        implementationClass = 'PublishedSettingsPlugin'
                    }
                }
            }
        """
        executer.inDirectory(file("plugin")).withTasks("publish").run()
        file("plugin").forceDeleteDir()
        mavenRepo.module("com.example", "plugin", "1.0").assertPublished()
    }
}
