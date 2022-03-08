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
        """
            plugins {
                id 'java'
            }
        """
    }

    static String applyGroovyPlugin() {
        """
            plugins {
                id 'groovy'
            }
        """
    }

    static String gradleApiDependency() {
        """
            dependencies {
                implementation gradleApi()
            }
        """
    }

    static String testKitDependency() {
        """
            dependencies {
                testImplementation gradleTestKit()
            }
        """
    }

    static String junitDependency() {
        """
            dependencies {
                testImplementation 'junit:junit:4.13.1'
            }
        """
    }

    static String spockDependency() {
        """
            dependencies {
                testImplementation('org.spockframework:spock-core:2.1-groovy-3.0') {
                    exclude group: 'org.codehaus.groovy'
                }
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

    static String testablePluginProject(String appliedLanguagePlugin = applyGroovyPlugin()) {
        StringBuilder buildFile = new StringBuilder()
        buildFile <<= appliedLanguagePlugin
        buildFile <<= mavenCentralRepository()
        buildFile <<= gradleApiDependency()
        buildFile <<= testKitDependency()
        buildFile <<= junitDependency()
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
