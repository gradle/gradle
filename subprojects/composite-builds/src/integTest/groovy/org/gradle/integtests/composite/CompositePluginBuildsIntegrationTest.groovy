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
import org.gradle.test.fixtures.file.TestFile

class CompositePluginBuildsIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    def "included plugin builds can contribute settings plugins"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}")
            }
        """

        when:
        succeeds()

        then:
        pluginBuild.assertSettingsPluginApplied()
    }

    def "included plugin builds can contribute project plugins"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")
            }
        """

        when:
        succeeds()

        then:
        pluginBuild.assertProjectPluginApplied()
    }

    def "included plugin build can contribute both settings and project plugins"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")
            }
        """

        when:
        succeeds()

        then:
        pluginBuild.assertSettingsPluginApplied()
        pluginBuild.assertProjectPluginApplied()
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
        def pluginBuild = pluginBuild("build-logic")
        publishSettingsPlugin(pluginBuild.settingsPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}")
            }
        """

        then:
        succeeds()
        pluginBuild.assertSettingsPluginApplied()
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
        def pluginBuild = pluginBuild("build-logic")
        publishSettingsPlugin(pluginBuild.settingsPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}") version "1.0"
            }
        """

        then:
        succeeds()
        outputContains("${pluginBuild.settingsPluginId} from repository applied")
        pluginBuild.assertSettingsPluginNotApplied()
    }

    def "settings plugin from included build is used over published plugin when version specified is not found in repository"() {
        given:
        def repoDeclaration = """
            repositories {
                maven {
                    url("${mavenRepo.uri}")
                }
            }
        """
        def pluginBuild = pluginBuild("build-logic")
        publishSettingsPlugin(pluginBuild.settingsPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}") version "2.0"
            }
        """

        then:
        succeeds()
        pluginBuild.assertSettingsPluginApplied()
    }

    def "project plugin from included build is used over published plugin when no version is specified"() {
        given:
        def repoDeclaration = """
            repositories {
                maven {
                    url("${mavenRepo.uri}")
                }
            }
        """
        def pluginBuild = pluginBuild("build-logic")
        publishProjectPlugin(pluginBuild.projectPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${pluginBuild.buildName}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")
            }
        """

        then:
        succeeds()
        pluginBuild.assertProjectPluginApplied()
    }

    def "published project plugin is used over included build plugin when version is specified"() {
        given:
        def repoDeclaration = """
            repositories {
                maven {
                    url("${mavenRepo.uri}")
                }
            }
        """
        def pluginBuild = pluginBuild("build-logic")
        publishProjectPlugin(pluginBuild.projectPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${pluginBuild.buildName}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}") version "1.0"
            }
        """

        then:
        succeeds()
        outputContains("${pluginBuild.projectPluginId} from repository applied")
        pluginBuild.assertProjectPluginNotApplied()
    }

    def "project plugin from included build is used over published plugin when version specified is not found in repository"() {
        given:
        def repoDeclaration = """
            repositories {
                maven {
                    url("${mavenRepo.uri}")
                }
            }
        """
        def pluginBuild = pluginBuild("build-logic")
        publishProjectPlugin(pluginBuild.projectPluginId, repoDeclaration)

        when:
        settingsFile << """
            pluginManagement {
                $repoDeclaration
                includeBuild("${pluginBuild.buildName}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}") version "2.0"
            }
        """

        then:
        succeeds()
        pluginBuild.assertProjectPluginApplied()
    }

    def "regular included build can not contribute settings plugins"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        settingsFile << """
            plugins {
                id("${pluginBuild.settingsPluginId}")
            }
            includeBuild("${pluginBuild.buildName}")
        """

        when:
        fails()

        then:
        failureDescriptionContains("Plugin [id: '${pluginBuild.settingsPluginId}'] was not found in any of the following sources:")
    }

    // Emit the deprecation warning in CompositeBuildPluginResolverContributor once we settle on and publicize the new API for plugin builds
    @NotYetImplemented
    def "regular included builds contributing project plugins is deprecated"() {
        given:
        def pluginBuild = pluginBuild("build-logic")
        settingsFile << """
            includeBuild("${pluginBuild.buildName}")
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")
            }
        """

        when:
        executer.expectDocumentedDeprecationWarning("Including builds that contribute Gradle plugins outside of pluginManagement {} block in settings file has been deprecated. This is scheduled to be removed in Gradle 8.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#included_builds_contributing_plugins")
        succeeds()

        then:
        pluginBuild.assertProjectPluginApplied()
    }

    def "included plugin build is not visible as library component"() {
        given:
        def build = pluginAndLibraryBuild("included-build")
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

    def "a build can be included both as a plugin build and as regular build and can contribute both plugins and library components"() {
        given:
        def build = pluginAndLibraryBuild("included-build")
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

    def "a build can be included both as a plugin build and as regular build and can contribute both settings plugins and library components"() {
        given:
        def build = pluginAndLibraryBuild("included-build")
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

    // Fails with config cache: Cannot find parent ClassLoaderScopeIdentifier{coreAndPlugins:settings} for child scope ClassLoaderScopeIdentifier{coreAndPlugins:settings:.../logic-1/buildSrc}
    @ToBeFixedForConfigurationCache
    def "library build included in plugin build can be used in settings plugin when such settings plugin is included in another build"() {
        given:
        def libraryBuild = pluginAndLibraryBuild("library")
        def pluginBuild = pluginBuild("plugin")

        pluginBuild.settingsFile << """
            includeBuild("../${libraryBuild.buildName}")
        """
        pluginBuild.buildFile << """
            dependencies {
                implementation("${libraryBuild.group}:${libraryBuild.buildName}")
            }
        """

        when:
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
            plugins {
                id("${pluginBuild.settingsPluginId}")
            }
        """

        then:
        succeeds()
        pluginBuild.assertSettingsPluginApplied()
    }

    def "library build included in plugin build can be used in project plugin when such project plugin is included in another build"() {
        given:
        def libraryBuild = pluginAndLibraryBuild("library")
        def pluginBuild = pluginBuild("plugin")

        pluginBuild.settingsFile << """
            includeBuild("../${libraryBuild.buildName}")
        """
        pluginBuild.buildFile << """
            dependencies {
                implementation("${libraryBuild.group}:${libraryBuild.buildName}")
            }
        """

        when:
        settingsFile << """
            pluginManagement {
                includeBuild("${pluginBuild.buildName}")
            }
        """
        buildFile << """
            plugins {
                id("${pluginBuild.projectPluginId}")
            }
        """

        then:
        succeeds()
        pluginBuild.assertProjectPluginApplied()
    }

    def "a build that applies an included settings plugin can be included in another build"() {
        given:
        def settingsPluginBuild = pluginBuild("settings")
        def lib1 = pluginAndLibraryBuild("lib1")

        when:
        lib1.settingsFile.setText("""
            pluginManagement {
                includeBuild("../${settingsPluginBuild.buildName}")
            }
            plugins {
                id("${settingsPluginBuild.settingsPluginId}")
            }
            rootProject.name="${lib1.buildName}"
        """)
        settingsFile << """
            includeBuild("${lib1.buildName}")
        """

        then:
        succeeds()
        settingsPluginBuild.assertSettingsPluginApplied()
    }

    private BuildLogicAndLibraryBuildFixture pluginAndLibraryBuild(String buildName) {
        return new BuildLogicAndLibraryBuildFixture(pluginBuild(buildName))
    }

    class BuildLogicAndLibraryBuildFixture {
        private final AbstractCompositeBuildIntegrationTest.PluginBuildFixture pluginBuild
        final TestFile settingsFile
        final TestFile buildFile
        final String buildName
        final String settingsPluginId
        final String projectPluginId
        final String group

        BuildLogicAndLibraryBuildFixture(AbstractCompositeBuildIntegrationTest.PluginBuildFixture pluginBuild) {
            this.pluginBuild = pluginBuild
            this.settingsFile = pluginBuild.settingsFile
            this.buildFile = pluginBuild.buildFile
            this.buildName = pluginBuild.buildName
            this.settingsPluginId = pluginBuild.settingsPluginId
            this.projectPluginId = pluginBuild.projectPluginId
            this.group = "com.example"
            pluginBuild.buildFile.setText("""
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
            pluginBuild.assertProjectPluginApplied()
        }

        void assertProjectPluginApplied() {
            pluginBuild.assertProjectPluginApplied()
        }
    }

    private void publishSettingsPlugin(String pluginId, String repoDeclaration) {
        publishPlugin(pluginId, repoDeclaration, "org.gradle.api.initialization.Settings")
    }

    private void publishProjectPlugin(String pluginId, String repoDeclaration) {
        publishPlugin(pluginId, repoDeclaration, "org.gradle.api.Project")
    }

    private void publishPlugin(String pluginId, String repoDeclaration, String pluginTarget) {
        file("plugin/src/main/java/PublishedPlugin.java") << """
            import org.gradle.api.Plugin;

            public class PublishedPlugin implements Plugin<$pluginTarget> {
                @Override
                public void apply($pluginTarget target) {
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
                        implementationClass = 'PublishedPlugin'
                    }
                }
            }
        """
        executer.inDirectory(file("plugin")).withTasks("publish").run()
        file("plugin").forceDeleteDir()
        mavenRepo.module("com.example", "plugin", "1.0").assertPublished()
    }
}
