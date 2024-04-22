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

package org.gradle.api.invocation

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.integtests.fixtures.KotlinDslTestUtil.getKotlinDslBuildSrcConfig

class GradleLifecycleIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile '''
            pluginManagement {
                includeBuild 'build-logic'
            }
            plugins {
                id 'my-settings-plugin'
            }
            dsl {
                parameter = "42"
            }
        '''
    }

    def configuredTaskRunsCorrectly() {
        run 'test'
        outputContains 'The parameter is `42`'
        true
    }

    def 'isolated beforeProject action given as Kotlin lambda can capture managed value'() {
        given:
        createDir('build-logic') {
            file('settings.gradle.kts') << ''
            file('build.gradle.kts') << """
                plugins {
                    `java-gradle-plugin`
                    `kotlin-dsl`
                }
                $kotlinDslBuildSrcConfig
            """
            file('src/main/kotlin/my/SettingsPluginDsl.kt') << '''
                package my
                interface SettingsPluginDsl {
                    val parameter: org.gradle.api.provider.Property<String>
                }
            '''
            file('src/main/kotlin/my-settings-plugin.settings.gradle.kts') << '''
                abstract class CustomTask : DefaultTask() {
                    @get:Input abstract val taskParameter: Property<String>
                    @TaskAction fun printParameter() {
                        println("The parameter is `${taskParameter.get()}`")
                    }
                }

                // Expose dsl to the user, the value will be isolated only after settings has been fully evaluated
                extensions.create<my.SettingsPluginDsl>("dsl").let { dsl ->
                    gradle.lifecycle.beforeProject {
                        tasks.register<CustomTask>("test") {
                            taskParameter = dsl.parameter
                        }
                    }
                }
            '''
        }

        expect:
        configuredTaskRunsCorrectly()
    }

    def 'isolated beforeProject action given as Java lambda can capture managed value'() {
        given:
        createDir('build-logic') {
            groovyFile file('build.gradle'), '''
                plugins {
                    id 'java'
                    id 'java-gradle-plugin'
                }
                gradlePlugin {
                    plugins {
                        mySettingsPlugin {
                            id = 'my-settings-plugin'
                            implementationClass = 'my.SettingsPlugin'
                        }
                    }
                }
            '''
            javaFile file('src/main/java/my/SettingsPlugin.java'), '''
                package my;

                import org.gradle.api.DefaultTask;
                import org.gradle.api.Plugin;
                import org.gradle.api.initialization.Settings;
                import org.gradle.api.provider.Property;
                import org.gradle.api.tasks.Input;
                import org.gradle.api.tasks.TaskAction;

                public class SettingsPlugin implements Plugin<Settings> {

                    public interface Dsl {
                        Property<String> getParameter();
                    }

                    public static abstract class TestTask extends DefaultTask {
                        @Input abstract Property<String> getTaskParameter();
                        @TaskAction void printParameter() {
                            getLogger().lifecycle("The parameter is `" + getTaskParameter().get() + "`");
                        }
                    }

                    @Override
                    public void apply(Settings target) {
                        // Expose dsl to the user, the value will be isolated only after settings has been fully evaluated
                        final Dsl dsl = target.getExtensions().create("dsl", Dsl.class);
                        target.getGradle().getLifecycle().beforeProject(project -> {
                            project.getTasks().register("test", TestTask.class, task -> {
                                task.getTaskParameter().set(dsl.getParameter());
                            });
                        });
                    }
                }
            '''
        }

        expect:
        configuredTaskRunsCorrectly()
    }
}
