/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.plugin.repository

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule
import spock.lang.Issue

class PluginManagementWithSettingsPluginIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    @Override
    def setup() {
        // To ensure no cached plugin resolution state at the start of each test
        executer.requireOwnGradleUserHomeDir()
    }

    @Issue("https://github.com/gradle/gradle/issues/19852")
    def "local settings plugin can define custom plugin repository"() {
        def projectPluginRepo = publishProjectPlugin()

        withLocalSettingsPlugin("""
            settings.getPluginManagement().getRepositories().maven(repo -> {
                try {
                    repo.setUrl(new ${URI.name}("$projectPluginRepo.uri"));
                } catch(${URISyntaxException.name} e) { }
            });
        """)

        buildFile << """
            plugins {
                id("test.test-plugin").version("1.0")
            }
        """

        when:
        run("pluginTask")

        then:
        outputContains("external plugin")
        // Ensure plugin portal is not unintentionally used
        pluginPortal.server.resetExpectations()
    }

    @Issue("https://github.com/gradle/gradle/issues/14536")
    def "local settings plugin can define custom plugin repository with exclusive content after remote settings pluging applied"() {
        def projectPluginRepo = publishProjectPlugin()
        publishRemoteSettingsPlugin()

        withLocalSettingsPlugin("""
            settings.getPluginManagement().getRepositories().exclusiveContent(spec -> {
                spec.forRepository(() -> {
                    return settings.getPluginManagement().getRepositories().maven(repo -> {
                        try {
                            repo.setUrl(new ${URI.name}("$projectPluginRepo.uri"));
                        } catch(${URISyntaxException.name} e) { }
                    });
                });
                spec.filter(desc -> desc.includeGroupByRegex(".*test.*"));
            });
        """)
        settingsFile << """
            plugins {
                id("test.remote-settings-plugin").version("1.0")
            }
        """

        buildFile << """
            plugins {
                id("test.test-plugin").version("1.0")
            }
        """

        when:
        run("pluginTask")

        then:
        outputContains("external plugin")
        // Ensure plugin portal is not unintentionally used
        pluginPortal.server.resetExpectations()
    }

    @Issue("https://github.com/gradle/gradle/issues/14536")
    def "init script can define repositories with exclusive content after remote settings plugin has been applied"() {
        def projectPluginRepo = publishProjectPlugin()
        publishRemoteSettingsPlugin()

        settingsFile << """
            plugins {
                id("test.remote-settings-plugin").version("1.0")
            }
        """

        def initScript = file("init.gradle")
        initScript << """
            settingsEvaluated { settings ->
                settings.pluginManagement {
                    repositories {
                        exclusiveContent {
                            forRepository {
                                maven {
                                    url = "$projectPluginRepo.uri"
                                }
                            }
                            filter {
                                includeGroupByRegex(".*test.*")
                            }
                        }
                    }
                }
            }
        """

        buildFile << """
            plugins {
                id("test.test-plugin").version("1.0")
            }
        """

        when:
        executer.withArguments("-I", initScript.absolutePath)
        run("pluginTask")

        then:
        outputContains("external plugin")
        // Ensure plugin portal is not unintentionally used
        pluginPortal.server.resetExpectations()
    }

    @Issue("https://github.com/gradle/gradle/issues/19990")
    def "build script can define repositories with exclusive content after settings plugin has been applied"() {
        def projectPluginRepo = publishProjectPlugin()
        publishRemoteSettingsPlugin()

        settingsFile << """
            plugins {
                id("test.remote-settings-plugin").version("1.0")
            }
        """

        buildScript("""
            buildscript {
                repositories {
                    exclusiveContent {
                        forRepository {
                            maven {
                                url = "$projectPluginRepo.uri"
                            }
                        }
                        filter {
                            includeGroupByRegex(".*test.*")
                        }
                    }
                }
                dependencies {
                    classpath "test:test:1.0"
                }
            }
        """)

        buildFile << """
            apply plugin: "test.test-plugin"
        """

        when:
        run("pluginTask")

        then:
        outputContains("external plugin")
        // Ensure plugin portal is not unintentionally used
        pluginPortal.server.resetExpectations()
    }

    private MavenFileRepository publishProjectPlugin() {
        def repo = new MavenFileRepository(file("project-plugins"))
        def pluginBuilder = new PluginBuilder(file("test-project-plugin"))
        pluginBuilder.addPluginWithPrintlnTask("pluginTask", "external plugin", "test.test-plugin")
        pluginBuilder.publishAs("test:test:1.0", repo, executer)
        return repo
    }

    private void publishRemoteSettingsPlugin() {
        def dir = file("test-settings-plugin")
        dir.file("src/main/java/org/gradle/test/SettingsPlugin.java") << """
            package org.gradle.test;

            import ${Settings.name};
            import ${Plugin.name};

            public class SettingsPlugin implements Plugin<Settings> {
                public void apply(Settings target) { }
            }
        """
        def pluginBuilder = new PluginBuilder(dir)
        pluginBuilder.addPluginId("test.remote-settings-plugin", "SettingsPlugin")
        pluginBuilder.publishAs("test:remote-settings-plugin:1.0", pluginPortal, executer)
        pluginPortal.expectPluginResolution("test.remote-settings-plugin", "1.0", "test", "remote-settings-plugin", "1.0")
    }

    private void withLocalSettingsPlugin(String applyMethod = "") {
        settingsFile << """
            pluginManagement {
                includeBuild("plugins")
            }
            plugins {
                id("test.settings-plugin")
            }
        """

        file("plugins/build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }
            gradlePlugin {
                plugins {
                    test {
                        id = "test.settings-plugin"
                        implementationClass = "test.SettingsPlugin"
                    }
                }
            }
        """
        file("plugins/src/main/java/test/SettingsPlugin.java") << """
            package test;
            import ${Plugin.name};
            import ${Settings.name};
            public class SettingsPlugin implements Plugin<Settings> {
                public void apply(Settings settings) {
                    $applyMethod
                }
            }
        """
    }
}
