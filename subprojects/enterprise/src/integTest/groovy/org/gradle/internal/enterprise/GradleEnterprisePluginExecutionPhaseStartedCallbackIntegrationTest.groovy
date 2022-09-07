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

package org.gradle.internal.enterprise

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GradleEnterprisePluginExecutionPhaseStartedCallbackIntegrationTest extends AbstractIntegrationSpec {

    def plugin = new GradleEnterprisePluginCheckInFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        plugin.publishDummyPlugin(executer)
    }

    def "receives execution phase started callback if build succeeds"() {
        given:
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        buildFile << """
            tasks.register("success")
        """
        when:
        succeeds "success"

        then:
        plugin.invokedExecutionPhaseStartedCallbackOnce(output)

        when:
        succeeds "success"

        then:
        plugin.invokedExecutionPhaseStartedCallbackOnce(output)
    }

    def "receives execution phase started callback if build fails"() {
        given:
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        buildFile << """
            tasks.register("failure") {
                doLast {
                    throw new GradleException("Expected failure")
                }
            }
        """
        when:
        fails "failure"

        then:
        plugin.invokedExecutionPhaseStartedCallbackOnce(output)

        when:
        fails "failure"

        then:
        plugin.invokedExecutionPhaseStartedCallbackOnce(output)
    }

    def "does not receive execution phase started callback if configuration fails"() {
        given:
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        buildFile << """
            tasks.register("t")
            throw new GradleException("Expected configuration failure")
        """
        when:
        fails "t"

        then:
        plugin.didNotInvokeExecutionPhaseStartedCallback(output)
    }

    def "receives execution phase started callback only once per build tree"() {
        given:
        createDir("buildSrc") {
            file("src/main/groovy/buildsrc-plugin.gradle") << """
                println "buildSrc project plugin applied"
            """
            file("build.gradle") << applyPrecompiledGroovyScriptPlugin()
        }

        def includedPlugin = createDir("included-plugin") {
            file("build.gradle") << applyPrecompiledGroovyScriptPlugin() << """
                gradlePlugin {
                    plugins.create("javaProjectPlugin") {
                        id = "project-plugin"
                        implementationClass = "ProjectPlugin"
                    }
                }
            """
            file("src/main/groovy/precompiled-project-plugin.gradle") << """
                println "precompiled project plugin applied"
            """
            file("src/main/java/ProjectPlugin.java").java("""
                import ${Plugin.name};
                import ${Project.name};

                public class ProjectPlugin implements Plugin<Project> {
                    @Override public void apply(Project p) {
                        System.out.println("Project plugin applied");
                    }
                }
            """)
        }


        def includedSettingsPlugin = createDir("included-settings-plugin") {
            file("build.gradle") << """
                ${applyPrecompiledGroovyScriptPlugin()}
                gradlePlugin {
                    plugins.create("javaSettingsPlugin") {
                        id = "settings-plugin"
                        implementationClass = "SettingsPlugin"
                    }
                }
            """
            file("src/main/java/SettingsPlugin.java") << """
                import ${Plugin.name};
                import ${Settings.name};

                public class SettingsPlugin implements Plugin<Settings> {
                    @Override public void apply(Settings s) {
                        System.out.println("Settings plugin applied");
                    }
                }
            """
            file("src/main/groovy/precompiled-settings-plugin.settings.gradle") << """
                println "precompiled settings plugin applied"
            """
        }
        def includedProject = createDir("included-project") {
            file("build.gradle") << """
                tasks.register("includedTask")
            """
        }
        settingsFile << """
            pluginManagement {
                repositories {
                    ${plugin.pluginRepository()}
                }
                includeBuild("${includedSettingsPlugin.name}")
            }
            plugins {
                ${plugin.pluginDependency()}
                id "settings-plugin"
                id "precompiled-settings-plugin"
            }

            includeBuild("$includedPlugin.name")
            includeBuild("$includedProject.name")
        """

        buildFile << """
            plugins {
                id "precompiled-project-plugin"
                id "project-plugin"
                id "buildsrc-plugin"
            }

            tasks.register("success") {
                dependsOn gradle.includedBuild('$includedProject.name').task(':includedTask')
            }
        """

        when:
        succeeds "success"

        then:
        plugin.invokedExecutionPhaseStartedCallbackOnce(output)

        when:
        succeeds "success"

        then:
        plugin.invokedExecutionPhaseStartedCallbackOnce(output)
    }

    def "receives execution phase started callback after TaskExecutionGraph ready callbacks"() {
        def whenReadyCallbackMsg = "TaskExecutionGraph.whenReady"
        given:
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        buildFile << """
            gradle.taskGraph.whenReady {
                println "$whenReadyCallbackMsg"
            }
            tasks.register("success")
        """
        when:
        succeeds "success"

        then:
        plugin.assertMessagePrecedesExecutionPhaseStartedCallback(output, whenReadyCallbackMsg)
    }

    def "receives execution phase started callback before task execution"() {
        def taskExecutionMsg = "Task.executed"
        given:
        settingsFile << plugin.pluginManagement() << plugin.plugins()
        buildFile << """
            tasks.register("success") {
                doFirst {
                    println "$taskExecutionMsg"
                }
            }
        """
        when:
        succeeds "success"

        then:
        plugin.assertMessageFollowsExecutionPhaseStartedCallback(output, taskExecutionMsg)
    }

    private static String applyPrecompiledGroovyScriptPlugin() {
        return """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
    }
}
