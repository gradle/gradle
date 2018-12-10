/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import javax.inject.Inject


class PluginServiceInjectionIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            import ${Inject.name}
        """
    }

    def "can apply a plugin with @Inject services constructor arg"() {
        buildFile << """
            class CustomPlugin implements Plugin<Project> {
                private final WorkerExecutor executor

                @Inject
                CustomPlugin(WorkerExecutor executor) {
                    this.executor = executor
                }
                
                void apply(Project p) {
                    println(executor != null ? "got it" : "NOT IT")
                }
            }
            
            apply plugin: CustomPlugin
        """

        expect:
        succeeds()
        outputContains("got it")
    }

    def "fails when plugin constructor is not annotated with @Inject"() {
        buildFile << """
            class CustomPlugin implements Plugin<Project> {
                CustomPlugin(WorkerExecutor executor) {
                }
                
                void apply(Project p) {
                }
            }
            
            apply plugin: CustomPlugin
        """

        expect:
        fails()
        failure.assertHasCause("Failed to apply plugin [class 'CustomPlugin']")
        failure.assertHasCause("Could not create plugin of type 'CustomPlugin'.")
        failure.assertHasCause("The constructor for class CustomPlugin should be annotated with @Inject.")
    }

    def "fails when plugin constructor requests unknown service"() {
        buildFile << """
            interface Unknown { }
            
            class CustomPlugin implements Plugin<Project> {
                @Inject
                CustomPlugin(Unknown x) {
                }
                
                void apply(Project p) {
                }
            }
            
            apply plugin: CustomPlugin
        """

        expect:
        fails()
        failure.assertHasCause("Failed to apply plugin [class 'CustomPlugin']")
        failure.assertHasCause("Could not create plugin of type 'CustomPlugin'.")
        failure.assertHasCause("Unable to determine CustomPlugin argument #1: missing parameter value of type interface Unknown, or no service of type interface Unknown")
    }

    // Document current behaviour
    def "fails when service injected using getter"() {
        buildFile << """
            class CustomPlugin implements Plugin<Project> {
                @Inject
                WorkerExecutor getExecutor() { }
                
                void apply(Project p) {
                    assert executor == null
                }
            }
            
            apply plugin: CustomPlugin
        """

        expect:
        succeeds()
    }
}
