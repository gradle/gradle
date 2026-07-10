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

package org.gradle.integtests.composite

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

abstract class AbstractCompositeBuildTaskExecutionIntegrationTest extends AbstractIntegrationSpec {
    /**
     * Adds an included build that produces a plugin with id 'test.plugin'
     */
    def addPluginIncludedBuild(TestFile rootDir, String group = "lib", String name = "lib") {
        rootDir.file("settings.gradle") << """
            rootProject.name="$name"
        """
        rootDir.file("build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }
            group = "$group"
            gradlePlugin {
                plugins {
                    p {
                        id = "test.plugin"
                        implementationClass = "test.PluginImpl"
                    }
                }
            }
        """
        rootDir.file("src/main/java/test/PluginImpl.java") << """
            package test;

            import ${Project.name};
            import ${Plugin.name};

            public class PluginImpl implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("greeting", task -> {
                        task.doLast(s -> System.out.println("Hello world"));
                    });
                }
            }
        """
    }

    /**
     * Adds an included build that produces a settings plugin with id 'test.plugin'
     */
    def addSettingsPluginIncludedBuild(TestFile rootDir, String group = "lib", String name = "lib") {
        rootDir.file("settings.gradle") << """
            rootProject.name="$name"
        """
        rootDir.file("build.gradle") << """
            plugins {
                id("java-gradle-plugin")
            }
            group = "$group"
            gradlePlugin {
                plugins {
                    p {
                        id = "test.plugin"
                        implementationClass = "test.PluginImpl"
                    }
                }
            }
        """
        rootDir.file("src/main/java/test/PluginImpl.java") << """
            package test;

            import ${Settings.name};
            import ${Plugin.name};

            public class PluginImpl implements Plugin<Settings> {
                public void apply(Settings settings) {
                    settings.getGradle().rootProject(p -> {
                        p.getTasks().register("greeting", task -> {
                            task.doLast(s -> System.out.println("Hello world"));
                        });
                    });
                }
            }
        """
    }
}
