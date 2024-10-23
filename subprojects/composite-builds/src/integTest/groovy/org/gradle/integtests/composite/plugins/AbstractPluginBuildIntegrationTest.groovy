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

package org.gradle.integtests.composite.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

abstract class AbstractPluginBuildIntegrationTest extends AbstractIntegrationSpec {

    PluginBuildFixture pluginBuild(String buildName, List<String> rootProjectPlugins = [], boolean useKotlinDSL = false) {
        return new PluginBuildFixture(buildName, rootProjectPlugins, useKotlinDSL)
    }

    PluginAndLibraryBuildFixture pluginAndLibraryBuild(String buildName) {
        return new PluginAndLibraryBuildFixture(pluginBuild(buildName))
    }

    PluginAndLibraryBuildFixture pluginAndLibraryBuild(PluginBuildFixture pluginBuildFixture) {
        return new PluginAndLibraryBuildFixture(pluginBuildFixture)
    }

    class PluginBuildFixture {
        final String buildName
        final String settingsPluginId
        final String projectPluginId

        final TestFile settingsFile
        final TestFile buildFile

        final TestFile settingsPluginFile
        final TestFile projectPluginFile
        final List<String> rootProjectsPlugins

        PluginBuildFixture(String buildName, List<String> rootProjectPlugins, boolean useKotlinDSL) {
            def fileExtension = useKotlinDSL ? '.gradle.kts' : '.gradle'
            def sourceDirectory = useKotlinDSL ? 'kotlin' : 'groovy'
            def pluginPluginId = useKotlinDSL
                ? '`kotlin-dsl`'
                : 'id("groovy-gradle-plugin")'

            this.buildName = buildName
            this.settingsPluginId = "${buildName}.settings-plugin"
            this.projectPluginId = "${buildName}.project-plugin"
            this.settingsFile = file("$buildName/settings${fileExtension}")
            this.buildFile = file("$buildName/build${fileExtension}")
            this.rootProjectsPlugins = rootProjectPlugins


            settingsFile << """
                rootProject.name = "$buildName"
            """
            buildFile << """
                plugins {
                    ${rootProjectPlugins.collect { """id("$it")""" }.join("\n")}
                    $pluginPluginId
                }
                repositories {
                    gradlePluginPortal()
                }
            """
            settingsPluginFile = file("$buildName/src/main/$sourceDirectory/${settingsPluginId}.settings${fileExtension}")
            settingsPluginFile << """
                println("$settingsPluginId applied")
            """
            projectPluginFile = file("$buildName/src/main/$sourceDirectory/${projectPluginId}${fileExtension}")
            projectPluginFile << """
                println("$projectPluginId applied")
            """
        }

        @Override
        String toString() {
            return buildName
        }

        void assertSettingsPluginApplied() {
            outputContains("$settingsPluginId applied")
        }

        void assertSettingsPluginNotApplied() {
            outputDoesNotContain("$settingsPluginId applied")
        }

        void assertProjectPluginApplied() {
            outputContains("$projectPluginId applied")
        }

        void assertProjectPluginNotApplied() {
            outputDoesNotContain("$projectPluginId applied")
        }
    }

    class PluginAndLibraryBuildFixture {
        private final PluginBuildFixture pluginBuild
        final TestFile settingsFile
        final TestFile buildFile
        final String buildName
        final String settingsPluginId
        final String projectPluginId
        final String group

        PluginAndLibraryBuildFixture(PluginBuildFixture pluginBuild) {
            this.pluginBuild = pluginBuild
            this.settingsFile = pluginBuild.settingsFile
            this.buildFile = pluginBuild.buildFile
            this.buildName = pluginBuild.buildName
            this.settingsPluginId = pluginBuild.settingsPluginId
            this.projectPluginId = pluginBuild.projectPluginId
            this.group = "com.example"
            pluginBuild.buildFile.setText("""
                plugins {
                    ${pluginBuild.rootProjectsPlugins.collect { """id("$it")""" }.join("\n")}
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

    void publishSettingsPlugin(String pluginId, String repoDeclaration) {
        publishPlugin(pluginId, repoDeclaration, "org.gradle.api.initialization.Settings")
    }

    void publishProjectPlugin(String pluginId, String repoDeclaration) {
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
