/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.jvm.plugins
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.test.fixtures.archive.JarTestFixture

class JvmComponentPluginIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)
    }

    def "does not create library or binaries when not configured"() {
        when:
        buildFile << """
    plugins {
        id 'jvm-component'
    }
    model {
        tasks {
            create("validate") {
                doLast {
                    assert \$("components").size() == 0
                    assert project.binaries.empty
                }
            }
        }
    }
"""
        then:
        succeeds "validate"

        and:
        !file("build").exists()
    }

    def "defines jvm library and binary model objects and lifecycle task"() {
        when:
        buildFile << """
    plugins {
        id 'jvm-component'
    }

    model {
        components {
            myLib(JvmLibrarySpec)
        }
        tasks {
            create("validate") {
            def components = \$("components")
                doLast {
                    assert components.size() == 1
                    def myLib = components.myLib
                    assert myLib.name == 'myLib'
                    assert myLib instanceof JvmLibrarySpec

                    assert myLib.sources.size() == 0

                    assert project.binaries.size() == 1
                    assert myLib.binaries.values() as Set == project.binaries as Set

                    def myLibJar = (project.binaries as List)[0]
                    assert myLibJar instanceof JarBinarySpec
                    assert myLibJar.name == 'myLibJar'
                    assert myLibJar.displayName == "Jar 'myLibJar'"

                    def binaryTask = project.tasks['myLibJar']
                    assert binaryTask.group == 'build'
                    assert binaryTask.description == "Assembles Jar 'myLibJar'."
                    assert myLibJar.buildTask == binaryTask

                    def jarTask = project.tasks['createMyLibJar']
                    assert jarTask instanceof org.gradle.jvm.tasks.Jar
                    assert jarTask.group == null
                    assert jarTask.description == "Creates the binary file for Jar 'myLibJar'."
                }
            }
        }
    }
"""

        then:
        succeeds "validate"
    }

    def "creates empty jar when no language sources available"() {
        given:
        buildFile << """
    plugins {
        id 'jvm-component'
    }

    model {
        components {
            myJvmLib(JvmLibrarySpec)
        }
    }
"""
        when:
        succeeds "myJvmLibJar"

        then:
        executed ":createMyJvmLibJar", ":myJvmLibJar"

        and:
        def jar = new JarTestFixture(file("build/jars/myJvmLibJar/myJvmLib.jar"))
        jar.hasDescendants()
    }

    def "can configure jvm binary"() {
        given:
        buildFile << '''
    plugins {
        id 'jvm-component'
    }

    model {
        components {
            myJvmLib(JvmLibrarySpec)
        }
        binaries {
            all { jar ->
                jar.jarFile = new File($("buildDir"), "bin/${jar.name}.bin")
            }
        }
    }
'''
        when:
        succeeds "myJvmLibJar"

        then:
        file("build/bin/myJvmLibJar.bin").assertExists()
    }

    def "can configure jvm binary for component"() {
        given:
        buildFile << '''
    plugins {
        id 'jvm-component'
    }

    model {
        components {
            myJvmLib(JvmLibrarySpec) {
                binaries {
                    all { jar ->
                        jar.jarFile = new File($("buildDir"), "bin/${jar.name}.bin")
                    }
                }
            }
        }
    }
'''
        when:
        succeeds "myJvmLibJar"

        then:
        file("build/bin/myJvmLibJar.bin").assertExists()
    }

    def "can specify additional builder tasks for binary"() {
        given:
        buildFile << '''
    plugins {
        id 'jvm-component'
    }

    model {
        components {
            myJvmLib(JvmLibrarySpec)
        }
        tasks {
            $("binaries").values().each { binary ->
                def taskName = "log" + binary.name.capitalize()
                create(taskName) { task ->
                    task.doLast {
                        println "Constructing " + binary.displayName
                    }
                }
                binary.buildTask.dependsOn(taskName)
            }
        }
    }
'''
        when:
        succeeds "myJvmLibJar"

        then:
        executed ":createMyJvmLibJar", ":logMyJvmLibJar", ":myJvmLibJar"

        and:
        output.contains("Constructing Jar 'myJvmLibJar'")
    }

    def "can define multiple jvm libraries in single project"() {
        when:
        buildFile << """
    plugins {
        id 'jvm-component'
    }

    model {
        components {
            myLibOne(JvmLibrarySpec)
            myLibTwo(JvmLibrarySpec)
        }
        tasks {
            create("validate") {
                def components = \$("components")
                doLast {
                    assert components.size() == 2
                    assert components.myLibOne instanceof JvmLibrarySpec
                    assert components.myLibTwo instanceof JvmLibrarySpec

                    assert project.binaries.size() == 2
                    assert project.binaries.myLibOneJar == components.myLibOne.binaries.values()[0]
                    assert project.binaries.myLibTwoJar == components.myLibTwo.binaries.values()[0]
                }
            }
        }
    }
"""

        then:
        succeeds "validate"
    }

    def "can build multiple jvm libraries in single project"() {
        given:
        buildFile << """
    plugins {
        id 'jvm-component'
    }

    model {
        components {
            myLibOne(JvmLibrarySpec)
            myLibTwo(JvmLibrarySpec)
        }
    }
"""
        when:
        succeeds "myLibOneJar"

        then:
        executed ":createMyLibOneJar", ":myLibOneJar"
        notExecuted ":myLibTwoJar"

        when:
        succeeds "assemble"

        then:
        executed ":createMyLibOneJar", ":myLibOneJar", ":createMyLibTwoJar", ":myLibTwoJar"
    }
}
