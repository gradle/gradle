/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

class ApplyPluginBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "captures plugin application events"() {
        given:
        file("build.gradle") << "apply plugin: 'java'"

        when:
        succeeds "build"

        then:
        def plugins = operations.all(ApplyPluginBuildOperationType) {
            it.details.targetType == "project" &&
                it.details.targetPath == ":" &&
                it.details.buildPath == ":"
        }

        def pluginIdByClass = plugins.details.collectEntries ( { [it.pluginClass, it.pluginId ] })
        def expectedPlugins = [
            "org.gradle.api.plugins.HelpTasksPlugin": "org.gradle.help-tasks",
            // This tests runs in :core using a reduced distribution
            // "org.gradle.buildinit.plugins.BuildInitPlugin": "org.gradle.build-init",
            // "org.gradle.buildinit.plugins.WrapperPlugin": "org.gradle.wrapper",
            "org.gradle.api.plugins.JavaPlugin": "org.gradle.java",
            "org.gradle.api.plugins.JavaBasePlugin": null,
            "org.gradle.api.plugins.JvmEcosystemPlugin": null,
            "org.gradle.api.plugins.JvmToolchainsPlugin": null,
            "org.gradle.api.plugins.BasePlugin": null,
            "org.gradle.language.base.plugins.LifecycleBasePlugin": null,
            "org.gradle.api.plugins.ReportingBasePlugin": null,
            "org.gradle.testing.base.plugins.TestSuiteBasePlugin": null,
            "org.gradle.api.plugins.JvmTestSuitePlugin": "org.gradle.jvm-test-suite",
        ]

        pluginIdByClass.size() == expectedPlugins.size() || pluginIdByClass.size() == expectedPlugins.size() + 2 // +2 if we run against the full distribution
        pluginIdByClass.entrySet().containsAll(expectedPlugins.entrySet())
    }

    def "captures gradle plugin"() {
        when:
        def initScript = file("init.gradle") << """
            class MyPlugin implements Plugin {
                void apply(t) {}
            }

            apply plugin: MyPlugin
        """

        succeeds("help", "-I", initScript.absolutePath)

        then:
        def ops = operations.all(ApplyPluginBuildOperationType) {
            it.details.targetType == "gradle"
        }

        ops.size() == 1
        def op = ops.first()
        op.details.pluginClass == "MyPlugin"
        op.details.buildPath == ":"
        op.details.targetPath == null
    }

    def "captures setting plugin"() {
        when:
        settingsFile << """
            class MyPlugin implements Plugin {
                void apply(t) {}
            }

            apply plugin: MyPlugin
        """

        succeeds("help")

        then:
        def ops = operations.all(ApplyPluginBuildOperationType) {
            it.details.targetType == "settings"
        }

        ops.size() == 1
        def op = ops.first()
        op.details.pluginClass == "MyPlugin"
        op.details.buildPath == ":"
        op.details.targetPath == null
    }

    def "uses target instead of parent"() {
        when:
        createDirs("a", "b")
        settingsFile << """
            include "a"
            include "b"
        """
        buildFile """
            class Plugin1 implements Plugin {
                void apply(project) {
                    project.rootProject.project(":b").apply(plugin: Plugin2)
                }
            }
            class Plugin2 implements Plugin {
                void apply(project) {

                }
            }

            project(":a").apply plugin: Plugin1
        """
        succeeds("help")

        then:
        def p1 = operations.first(ApplyPluginBuildOperationType) {
            it.details.pluginClass == "Plugin1"
        }
        p1.details.targetPath == ":a"

        def children = operations.search(p1) {
            ApplyPluginBuildOperationType.Details.isAssignableFrom(it.detailsType)
        }

        children.size() == 1
        def p2 = children.first()

        p2.details.targetPath == ":b"
        p2.details.pluginClass == "Plugin2"
    }

    def "associates target to correct build"() {
        when:
        settingsFile << """
            includeBuild "a"
            includeBuild "b"
        """
        file("a/settings.gradle") << ""
        file("b/settings.gradle") << ""
        file("a/build.gradle") << """
            class PluginA implements Plugin {
                void apply(project) {

                }
            }
            apply plugin: PluginA
        """
        file("b/build.gradle") << """
            class PluginB implements Plugin {
                void apply(project) {

                }
            }
            apply plugin: PluginB
        """
        buildFile """
            class PluginRoot implements Plugin {
                void apply(project) {

                }
            }

            apply plugin: PluginRoot
        """
        succeeds("help")

        then:
        def ops = operations.all(ApplyPluginBuildOperationType) {
            it.details.pluginClass.startsWith("Plugin")
        }

        ops.size() == 3
        ops.find { it.details.buildPath == ":" }.details.pluginClass == "PluginRoot"
        ops.find { it.details.buildPath == ":a" }.details.pluginClass == "PluginA"
        ops.find { it.details.buildPath == ":b" }.details.pluginClass == "PluginB"
    }

}
