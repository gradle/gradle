/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.code

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Integration tests for reading {@link UserCodeSource} information from tasks.
 */
class UserCodeSourceIntegrationTest extends AbstractIntegrationSpec {
    def "can read task user code source"() {
        given:
        buildFile """
            task foo {
                doLast {
                    println "Hello from \${userCodeSource.displayName}"
                }
            }
        """

        when:
        run("foo")

        then:
        outputContains("Hello from build file 'build.gradle'")
    }

    def "can read task user code source for task registered by plugin"() {
        given:
        buildFile """
            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register("foo") {
                        doLast {
                            println "Hello from \${userCodeSource.displayName}"
                        }
                    }
                }
            }

            apply plugin: MyPlugin
        """

        when:
        run("foo", "--scan")

        then:
        outputContains("Hello from plugin class 'MyPlugin'")
    }

    def "can read task user code source for task registered by plugin in afterEvaluate"() {
        given:
        buildFile """
            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.afterEvaluate { p ->
                        p.tasks.register("foo") {
                            doLast {
                                println "Hello from \${userCodeSource.displayName}"
                            }
                        }
                    }
                }
            }

            apply plugin: MyPlugin
        """

        when:
        run("foo")

        then:
        outputContains("Hello from plugin class 'MyPlugin'")
    }
}
