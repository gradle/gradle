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

package org.gradle.configurationcache

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.test.fixtures.file.TestFile

class ConfigurationCacheIncludedBuildLogicIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    TestFile pluginSourceFile

    def setup() {
        settingsFile << "includeBuild('build-logic')"
        def rootDir = file("build-logic")
        rootDir.file("settings.gradle") << """
            rootProject.name="lib"
        """
        rootDir.file("build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }
            group="lib"
            gradlePlugin {
                plugins {
                    p {
                        id = "test.plugin"
                        implementationClass = "test.PluginImpl"
                    }
                }
            }
        """
        pluginSourceFile = rootDir.file("src/main/java/test/PluginImpl.java")
        pluginSourceFile << """
            package test;

            import ${Project.name};
            import ${Plugin.name};

            public class PluginImpl implements Plugin<Project> {
                public void apply(Project project) { }
            }
        """
        buildFile("""
            plugins {
                id("test.plugin")
            }
        """)
    }

    def "does not run build logic tasks when loaded from cache"() {
        def fixture = newConfigurationCacheFixture()
        buildFile("""
            task assemble {
            }
        """)

        when:
        configurationCacheRun("assemble")

        then:
        result.assertTasksExecuted(
            ":build-logic:compileJava",
            ":build-logic:pluginDescriptors",
            ":build-logic:processResources",
            ":build-logic:classes",
            ":build-logic:jar",
            ":assemble")
        fixture.assertStateStored()

        when:
        configurationCacheRun("assemble")

        then:
        result.assertTasksExecuted(":assemble")
        fixture.assertStateLoaded()

        when:
        pluginSourceFile << """
            // some change
        """
        configurationCacheRun("assemble")

        then:
        result.assertTasksExecuted(
            ":build-logic:compileJava",
            ":build-logic:pluginDescriptors",
            ":build-logic:processResources",
            ":build-logic:classes",
            ":build-logic:jar",
            ":assemble")
        fixture.assertStateStored()
    }

    def "does not run build logic tasks when loaded from the cache even when the tasks are requested on the command-line"() {
        def fixture = newConfigurationCacheFixture()

        when:
        configurationCacheRun(":build-logic:classes")

        then:
        result.assertTasksExecuted(
            ":build-logic:compileJava",
            ":build-logic:pluginDescriptors",
            ":build-logic:processResources",
            ":build-logic:classes",
            ":build-logic:jar")
        fixture.assertStateStored()

        when:
        configurationCacheRun(":build-logic:classes")

        then:
        result.assertTasksExecuted()
        fixture.assertStateLoaded()

        when:
        pluginSourceFile << """
            // some change
        """
        configurationCacheRun(":build-logic:classes")

        then:
        result.assertTasksExecuted(
            ":build-logic:compileJava",
            ":build-logic:pluginDescriptors",
            ":build-logic:processResources",
            ":build-logic:classes",
            ":build-logic:jar")
        fixture.assertStateStored()
    }

    def "does not run build logic tasks when loaded from the cache even when the tasks are also dependencies of requested tasks"() {
        def fixture = newConfigurationCacheFixture()
        buildFile("""
            plugins {
                id("java-library")
            }
            dependencies {
                implementation("lib:lib:1.0")
            }
        """)

        when:
        configurationCacheRun("assemble")

        then:
        result.assertTasksExecuted(
            ":build-logic:compileJava",
            ":build-logic:pluginDescriptors",
            ":build-logic:processResources",
            ":build-logic:classes",
            ":build-logic:jar",
            ":processResources",
            ":compileJava",
            ":classes",
            ":jar",
            ":assemble"
        )
        fixture.assertStateStored()

        when:
        configurationCacheRun("assemble")

        then:
        result.assertTasksExecuted(
            ":processResources",
            ":compileJava",
            ":classes",
            ":jar",
            ":assemble"
        )
        fixture.assertStateLoaded()

        when:
        pluginSourceFile << """
            // some change
        """
        configurationCacheRun("assemble")

        then:
        result.assertTasksExecuted(
            ":build-logic:compileJava",
            ":build-logic:pluginDescriptors",
            ":build-logic:processResources",
            ":build-logic:classes",
            ":build-logic:jar",
            ":processResources",
            ":compileJava",
            ":classes",
            ":jar",
            ":assemble")
        fixture.assertStateStored()
    }
}
