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
import org.gradle.test.fixtures.plugin.PluginBuilder
import spock.lang.Issue

class PluginManagementFromSettingsPluginIntegrationTest extends AbstractIntegrationSpec {
    @Issue("https://github.com/gradle/gradle/issues/19852")
    def "local settings plugin can define custom plugin repository"() {
        def pluginBuilder = new PluginBuilder(file("external-plugin"))
        pluginBuilder.addPluginWithPrintlnTask("pluginTask", "external plugin", "test.test-plugin")
        def repo = mavenRepo
        pluginBuilder.publishAs("test:test:1.0", repo, executer)

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
            import ${URI.name};
            import ${URISyntaxException.name};
            public class SettingsPlugin implements Plugin<Settings> {
                public void apply(Settings settings) {
                    settings.getPluginManagement().getRepositories().maven(repo -> {
                        try {
                            repo.setUrl(new URI("$repo.uri"));
                        } catch(URISyntaxException e) { }
                    });
                }
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
    }
}
