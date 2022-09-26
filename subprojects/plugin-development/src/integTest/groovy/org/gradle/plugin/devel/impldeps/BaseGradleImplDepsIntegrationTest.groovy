/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.devel.impldeps

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

abstract class BaseGradleImplDepsIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.requireOwnGradleUserHomeDir()
    }

    static String applyJavaPlugin() {
        applyPlugins(['java'])
    }

    static String applyGroovyPlugin() {
        applyPlugins(['groovy'])
    }

    static String applyPlugins(List<String> plugins) {
        """
            plugins {
                ${plugins.collect { "id '$it'\n"}.join('')}
            }
        """
    }

    static String excludeGroovy() {
        applyPlugins(['groovy'])
    }

    static String gradleApiDependency() {
        """
            dependencies {
                implementation gradleApi()
            }
        """
    }

    static String customGroovyPlugin() {
        """
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class MyPlugin implements Plugin<Project> {
                @Override
                void apply(Project project) {
                    println 'Plugin applied!'
                }
            }
        """
    }
    static String testablePluginProject(List<String> plugins = ['groovy-gradle-plugin']) {
        StringBuilder buildFile = new StringBuilder()
        buildFile << applyPlugins(plugins)
        buildFile << mavenCentralRepository()
        buildFile << """
            testing {
                suites {
                    test {
                        useJUnit()
                    }
                }
            }

            gradlePlugin {
                plugins {
                    plugin {
                        id = "my-plugin"
                        implementationClass = "MyPlugin"
                    }
                }
            }
        """
        buildFile.toString()
    }

    static void assertSingleGenerationOutput(String output, String regex) {
        def pattern = /\b${regex}\b/
        def matcher = output =~ pattern
        assert matcher.count == 1
    }

    static void assertNoGenerationOutput(String output, String regex) {
        def pattern = /\b${regex}\b/
        def matcher = output =~ pattern
        assert matcher.count == 0
    }
}
