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
        def buildLogicBuild = buildLogicBuild("build-logic")
        settingsFile << """
            pluginManagement {
                includeBuild("${buildLogicBuild.buildName}")
            }
            plugins {
                id("${buildLogicBuild.settingsPluginId}")
            }
        """

        when:
        succeeds()

        then:
        buildLogicBuild.assertSettingsPluginApplied()
    }

    def "included build logic builds can contribute project plugins"() {
        given:
        def buildLogicBuild = buildLogicBuild("build-logic")
        settingsFile << """
            pluginManagement {
                includeBuild("${buildLogicBuild.buildName}")
            }
        """
        buildFile << """
            plugins {
                id("${buildLogicBuild.projectPluginId}")
            }
        """

        when:
        succeeds()

        then:
        buildLogicBuild.assertProjectPluginApplied()
    }

    def "included build logic build can contribute both settings and project plugins"() {
        given:
        def buildLogicBuild = buildLogicBuild("build-logic")
        settingsFile << """
            pluginManagement {
                includeBuild("${buildLogicBuild.buildName}")
            }
            plugins {
                id("${buildLogicBuild.settingsPluginId}")
            }
        """
        buildFile << """
            plugins {
                id("${buildLogicBuild.projectPluginId}")
            }
        """

        when:
        succeeds()

        then:
        buildLogicBuild.assertSettingsPluginApplied()
        buildLogicBuild.assertProjectPluginApplied()
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
        def buildLogicBuild = buildLogicBuild("build-logic")
        publishSettingsPlugin(buildLogicBuild.settingsPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${buildLogicBuild.buildName}")
            }
            plugins {
                id("${buildLogicBuild.settingsPluginId}")
            }
        """

        then:
        succeeds()
        buildLogicBuild.assertSettingsPluginApplied()
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
        def buildLogicBuild = buildLogicBuild("build-logic")
        publishSettingsPlugin(buildLogicBuild.settingsPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${buildLogicBuild.buildName}")
            }
            plugins {
                id("${buildLogicBuild.settingsPluginId}") version "1.0"
            }
        """

        then:
        succeeds()
        outputContains("${buildLogicBuild.settingsPluginId} from repository applied")
        buildLogicBuild.assertSettingsPluginNotApplied()
    }

    def "regular included build can not contribute settings plugins"() {
        given:
        def buildLogicBuild = buildLogicBuild("build-logic")
        settingsFile << """
            plugins {
                id("${buildLogicBuild.settingsPluginId}")
            }
            includeBuild("${buildLogicBuild.buildName}")
        """

        when:
        fails()

        then:
        failureDescriptionContains("Plugin [id: '${buildLogicBuild.settingsPluginId}'] was not found in any of the following sources:")
    }

    // Emit the deprecation warning in CompositeBuildPluginResolverContributor once we settle on and publicize the new API for build logic builds
    @NotYetImplemented
    def "regular included builds contributing project plugins is deprecated"() {
        given:
        def buildLogicBuild = buildLogicBuild("build-logic")
        settingsFile << """
            includeBuild("${buildLogicBuild.buildName}")
        """
        buildFile << """
            plugins {
                id("${buildLogicBuild.projectPluginId}")
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Including builds that contribute Gradle plugins outside of pluginManagement {} block in settings file has been deprecated. This is scheduled to be removed in Gradle 8.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#included_builds_contributing_plugins")
        succeeds()

        then:
        buildLogicBuild.assertProjectPluginApplied()
    }

    def "included build logic build is not visible as library component"() {
        given:
        def build = buildLogicAndLibraryBuild("included-build")
        settingsFile << """
            pluginManagement {
                includeBuild("${build.buildName}")
            }
        """

        when:
        buildFile << """
            plugins {
                id("java-library")
            }
            dependencies {
                implementation("${build.group}:${build.buildName}")
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
        def build = buildLogicAndLibraryBuild("included-build")
        settingsFile << """
            pluginManagement {
                includeBuild("${build.buildName}")
            }
            includeBuild("${build.buildName}")
        """

        when:
        buildFile << """
            plugins {
                id("java-library")
                id("${build.projectPluginId}")
            }
            dependencies {
                implementation("${build.group}:${build.buildName}")
            }
        """
        file("src/main/java/Foo.java") << """
            class Foo { Bar newBar() { return new Bar(); }}
        """

        then:
        succeeds("build")
        assertTaskExecuted(":${build.buildName}", ':compileJava')
        assertTaskExecuted(':', ':compileJava')
        build.assertProjectPluginApplied()
    }

    def "a build can be included both as a build logic build and as regular build and can contribute both settings plugins and library components"() {
        given:
        def build = buildLogicAndLibraryBuild("included-build")
        settingsFile << """
            pluginManagement {
                includeBuild("${build.buildName}")
            }
            plugins {
                id("${build.settingsPluginId}")
            }
            includeBuild("${build.buildName}")
        """

        when:
        buildFile << """
            plugins {
                id("java-library")
                id("${build.projectPluginId}")
            }
            dependencies {
                implementation("${build.group}:${build.buildName}")
            }
        """
        file("src/main/java/Foo.java") << """
            class Foo { Bar newBar() { return new Bar(); }}
        """

        then:
        succeeds("build")
        assertTaskExecuted(":${build.buildName}", ':compileJava')
        assertTaskExecuted(':', ':compileJava')
        build.assertSettingsPluginApplied()
        build.assertProjectPluginApplied()
    }

    private BuildLogicAndLibraryBuildFixture buildLogicAndLibraryBuild(String buildName) {
        return new BuildLogicAndLibraryBuildFixture(buildLogicBuild(buildName))
    }

    class BuildLogicAndLibraryBuildFixture {
        private final AbstractCompositeBuildIntegrationTest.BuildLogicBuildFixture buildLogicBuild
        final String buildName
        final String settingsPluginId
        final String projectPluginId
        final String group

        BuildLogicAndLibraryBuildFixture(AbstractCompositeBuildIntegrationTest.BuildLogicBuildFixture buildLogicBuild) {
            this.buildLogicBuild = buildLogicBuild
            this.buildName = buildLogicBuild.buildName
            this.settingsPluginId = buildLogicBuild.settingsPluginId
            this.projectPluginId = buildLogicBuild.projectPluginId
            this.group = "com.example"
            buildLogicBuild.buildFile.setText("""
                plugins {
                    id("groovy-gradle-plugin")
                    id("java-library")
                }

                group = "${group}"
                version = "1.0"
            """)
            file("$buildName/src/main/java/Bar.java") << """
                public class Bar {}
            """
        }

        void assertSettingsPluginApplied() {
            buildLogicBuild.assertProjectPluginApplied()
        }

        void assertProjectPluginApplied() {
            buildLogicBuild.assertProjectPluginApplied()
        }
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
