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

package org.gradle.instantexecution.inputs.undeclared

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.test.fixtures.file.TestFile

trait GroovyPluginImplementation {
    void groovyDsl(TestFile sourceFile) {
        sourceFile << """
            println("apply GET_PROPERTY = " + System.getProperty("GET_PROPERTY"))
            println("apply GET_PROPERTY_OR_DEFAULT = " + System.getProperty("GET_PROPERTY_OR_DEFAULT", "default"))
            tasks.register("thing") {
                doLast {
                    println("task GET_PROPERTY = " + System.getProperty("GET_PROPERTY"))
                    println("task GET_PROPERTY_OR_DEFAULT = " + System.getProperty("GET_PROPERTY_OR_DEFAULT", "default"))
                }
            }
        """
    }

    void dynamicGroovyPlugin(TestFile sourceFile) {
        sourceFile << """
            import ${Project.name}
            import ${Plugin.name}

            class SneakyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    // Static method call
                    def value = System.getProperty("GET_PROPERTY")
                    println("apply GET_PROPERTY = " + value)

                    value = System.getProperty("GET_PROPERTY_OR_DEFAULT", "default")
                    println("apply GET_PROPERTY_OR_DEFAULT = " + value)

                    // Instance call
                    def sys = System
                    println("apply INSTANCE = " + sys.getProperty("INSTANCE"))

                    // Call from closure
                    def cl = { p ->
                        println("\$p CLOSURE = " + sys.getProperty("CLOSURE"))
                    }
                    cl("apply")

                    project.tasks.register("thing") { t ->
                        t.doLast {
                            value = System.getProperty("GET_PROPERTY")
                            println("task GET_PROPERTY = " + value)

                            value = System.getProperty("GET_PROPERTY_OR_DEFAULT", "default")
                            println("task GET_PROPERTY_OR_DEFAULT = " + value)

                            println("task INSTANCE = " + sys.getProperty("INSTANCE"))

                            cl("task")
                        }
                    }
                }
            }
        """
    }

    void staticGroovyPlugin(TestFile sourceFile) {
        sourceFile << """
            import ${Project.name}
            import ${Plugin.name}

            @${CompileStatic.name}
            class SneakyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    // Static method call
                    def value = System.getProperty("GET_PROPERTY")
                    println("apply GET_PROPERTY = " + value)

                    value = System.getProperty("GET_PROPERTY_OR_DEFAULT", "default")
                    println("apply GET_PROPERTY_OR_DEFAULT = " + value)

                    // Instance call
                    def sys = System
                    println("apply INSTANCE = " + sys.getProperty("INSTANCE"))

                    // Call from closure
                    def cl = { p ->
                        println("\$p CLOSURE = " + sys.getProperty("CLOSURE"))
                    }
                    cl("apply")

                    project.tasks.register("thing") { t ->
                        t.doLast {
                            value = System.getProperty("GET_PROPERTY")
                            println("task GET_PROPERTY = " + value)

                            value = System.getProperty("GET_PROPERTY_OR_DEFAULT", "default")
                            println("task GET_PROPERTY_OR_DEFAULT = " + value)

                            println("task INSTANCE = " + sys.getProperty("INSTANCE"))

                            cl("task")
                        }
                    }
                }
            }
        """
    }
}
