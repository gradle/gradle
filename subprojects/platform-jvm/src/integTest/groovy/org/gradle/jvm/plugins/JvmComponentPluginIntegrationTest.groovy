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
import org.gradle.test.fixtures.archive.JarTestFixture
import spock.lang.Ignore

class JvmComponentPluginIntegrationTest extends AbstractIntegrationSpec {
    def "does not create library or binaries when not configured"() {
        when:
        buildFile << """
    plugins {
        id 'jvm-component'
    }
    task validate << {
        assert componentSpecs.empty
        assert binaries.empty
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
    }

    task validate << {
        assert componentSpecs.size() == 1
        def myLib = componentSpecs.myLib
        assert myLib.name == 'myLib'
        assert myLib == componentSpecs['myLib']
        assert myLib instanceof JvmLibrarySpec

        assert myLib.sources.size() == 0

        assert binaries.size() == 1
        assert myLib.binaries as Set == binaries as Set

        def myLibJar = (binaries as List)[0]
        assert myLibJar instanceof JarBinarySpec
        assert myLibJar.name == 'myLibJar'
        assert myLibJar.displayName == "Jar 'myLibJar'"

        def binaryTask = tasks['myLibJar']
        assert binaryTask.group == 'build'
        assert binaryTask.description == "Assembles Jar 'myLibJar'."
        assert myLibJar.buildTask == binaryTask

        def jarTask = tasks['createMyLibJar']
        assert jarTask instanceof org.gradle.jvm.tasks.Jar
        assert jarTask.group == null
        assert jarTask.description == "Creates the binary file for Jar 'myLibJar'."
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
        buildFile << """
    plugins {
        id 'jvm-component'
    }

    model {
        components {
            myJvmLib(JvmLibrarySpec)
        }
        jvm {
            allBinaries { jar ->
                jar.jarFile = file("\${project.buildDir}/bin/\${jar.name}.bin")
            }
        }
    }
"""
        when:
        succeeds "myJvmLibJar"

        then:
        file("build/bin/myJvmLibJar.bin").assertExists()
    }

    @Ignore("Not yet implemented")
    def "can configure jvm binary for component"() {
        given:
        buildFile << """
    plugins {
        id 'jvm-component'
    }

    model {
        components {
            myJvmLib(JvmLibrarySpec) {
                binaries.withType(JarBinarySpec) { jar ->
                    jar.jarFile = file("\${project.buildDir}/bin/\${jar.name}.bin")
                }
            }
        }
    }
"""
        when:
        succeeds "myJvmLibJar"

        then:
        file("build/bin/myJvmLibJar.bin").assertExists()
    }

    def "can specify additional builder tasks for binary"() {
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
    binaries.all { binary ->
        def logTask = project.tasks.create("log_\${binary.name}") {
            doLast {
                println "Constructing \${binary.displayName}"
            }
        }
        binary.builtBy(logTask)
    }
"""
        when:
        succeeds "myJvmLibJar"

        then:
        executed ":createMyJvmLibJar", ":log_myJvmLibJar", ":myJvmLibJar"

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
    }

    task validate << {
        assert componentSpecs.size() == 2
        assert componentSpecs.myLibOne instanceof JvmLibrarySpec
        assert componentSpecs.myLibTwo instanceof JvmLibrarySpec

        assert binaries.size() == 2
        assert binaries.myLibOneJar == componentSpecs.myLibOne.binaries[0]
        assert binaries.myLibTwoJar == componentSpecs.myLibTwo.binaries[0]
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