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
import org.gradle.integtests.fixtures.UnsupportedWithInstantExecution
import org.gradle.test.fixtures.archive.JarTestFixture

@UnsupportedWithInstantExecution(because = "software model")
class JvmComponentPluginIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
    }

    def "does not create library or binaries when not configured"() {
        when:
        buildFile << '''
            plugins {
                id 'jvm-component'
            }
            model {
                tasks {
                    create("validate") {
                        doLast {
                            assert $.components.size() == 0
                            assert $.binaries.size() == 0
                            assert $.sources.size() == 0
                        }
                    }
                }
            }
        '''
        then:
        succeeds "validate"

        and:
        !file("build").exists()
    }

    def "defines jvm library and binary model objects and lifecycle task"() {
        when:
        buildFile << '''
            plugins {
                id 'jvm-component'
            }

            model {
                components {
                    myLib(JvmLibrarySpec)
                }
                tasks {
                    create("validate") {
                        doLast {
                            def components = $.components
                            def binaries = $.binaries
                            assert components.size() == 1
                            def myLib = components.myLib
                            assert myLib.name == 'myLib'
                            assert myLib instanceof JvmLibrarySpec

                            assert myLib.sources.size() == 0

                            assert binaries.size() == 1
                            assert myLib.binaries.values() as Set == binaries as Set

                            def myLibJar = (binaries.values() as List)[0]
                            assert myLibJar instanceof JarBinarySpec
                            assert myLibJar.name == 'jar'
                            assert myLibJar.displayName == "Jar 'myLib:jar'"

                            def binaryTask = project.tasks['myLibJar']
                            assert binaryTask.group == 'build'
                            assert binaryTask.description == "Assembles Jar 'myLib:jar'."
                            assert myLibJar.buildTask == binaryTask

                            def jarTask = project.tasks['createMyLibJar']
                            assert jarTask instanceof org.gradle.jvm.tasks.Jar
                            assert jarTask.group == null
                            assert jarTask.description == "Creates the binary file for Jar 'myLib:jar'."
                        }
                    }
                }
            }
        '''
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
        def jar = new JarTestFixture(file("build/jars/myJvmLib/jar/myJvmLib.jar"))
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
                        jar.jarFile = new File($("buildDir"), "bin/${jar.projectScopedName}.bin")
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
                                jar.jarFile = new File($("buildDir"), "bin/${jar.projectScopedName}.bin")
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
                    $.binaries.values().each { binary ->
                        def taskName = binary.tasks.taskName('log')
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
        output.contains("Constructing Jar 'myJvmLib:jar'")
    }

    def "can define multiple jvm libraries in single project"() {
        when:
        buildFile << '''
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
                        def components = $.components
                        def binaries = $.binaries
                        doLast {
                            assert components.size() == 2
                            assert components.myLibOne instanceof JvmLibrarySpec
                            assert components.myLibTwo instanceof JvmLibrarySpec

                            assert binaries.size() == 2
                            assert binaries.myLibOneJar == components.myLibOne.binaries.values()[0]
                            assert binaries.myLibTwoJar == components.myLibTwo.binaries.values()[0]
                        }
                    }
                }
            }
        '''
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
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        succeeds "assemble"

        then:
        executed ":createMyLibOneJar", ":myLibOneJar", ":createMyLibTwoJar", ":myLibTwoJar"
    }
}
