/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.language.assembler.plugins

import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.language.assembler.AssemblerSourceSet
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.nativebinaries.ExecutableBinary
import org.gradle.nativebinaries.NativeBinary
import org.gradle.nativebinaries.SharedLibraryBinary
import org.gradle.nativebinaries.StaticLibraryBinary
import org.gradle.nativebinaries.language.assembler.tasks.Assemble
import org.gradle.util.TestUtil
import spock.lang.Specification

class AssemblerNativeBinariesPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    def "creates asm source set with conventional locations for components"() {
        when:
        dsl {
            apply plugin: AssemblerNativeBinariesPlugin
            executables {
                exe {}
            }
            libraries {
                lib {}
            }
        }

        then:
        def sourceSets = project.sources
        sourceSets.size() == 2
        sourceSets*.name == ["exe", "lib"]

        and:
        sourceSets.exe.asm instanceof AssemblerSourceSet
        sourceSets.exe.asm.source.srcDirs == [project.file("src/exe/asm")] as Set
        project.executables.exe.source == [sourceSets.exe.asm] as Set

        and:
        sourceSets.lib instanceof FunctionalSourceSet
        sourceSets.lib.asm instanceof AssemblerSourceSet
        sourceSets.lib.asm.source.srcDirs == [project.file("src/lib/asm")] as Set
        project.libraries.lib.source == [sourceSets.lib.asm] as Set
    }

    def "can configure source set locations"() {
        given:
        dsl {
            apply plugin: AssemblerNativeBinariesPlugin
            sources {
                exe {
                    asm {
                        source {
                            srcDirs "d1", "d2"
                        }
                    }
                }
                lib {
                    asm {
                        source {
                            srcDirs "d3"
                        }
                    }
                }
            }
        }

        expect:
        project.sources.exe.asm.source.srcDirs*.name == ["d1", "d2"]
        project.sources.lib.asm.source.srcDirs*.name == ["d3"]
    }

    def "creates assemble tasks for each executable source set"() {
        when:
        dsl {
            apply plugin: AssemblerNativeBinariesPlugin
            sources {
                test {
                    anotherOne(AssemblerSourceSet) {}
                }
            }
            executables {
                test {
                    binaries.all { NativeBinary binary ->
                        binary.assembler.args "ARG1", "ARG2"
                    }
                }
            }
        }

        then:
        ExecutableBinary binary = project.binaries.testExecutable
        binary.tasks.withType(Assemble)*.name == ["assembleTestExecutableTestAnotherOne", "assembleTestExecutableTestAsm"]

        and:
        binary.tasks.withType(Assemble).each { compile ->
            compile instanceof Assemble
            compile.toolChain == binary.toolChain
            compile.assemblerArgs == ["ARG1", "ARG2"]
        }

        and:
        def linkTask = binary.tasks.link
        linkTask TaskDependencyMatchers.dependsOn("assembleTestExecutableTestAnotherOne", "assembleTestExecutableTestAsm")
    }

    def "creates assemble tasks for each library source set"() {
        when:
        dsl {
            apply plugin: AssemblerNativeBinariesPlugin
            sources {
                test {
                    anotherOne(AssemblerSourceSet) {}
                }
            }
            libraries {
                test {
                    binaries.all {
                        assembler.args "ARG1", "ARG2"
                    }
                    binaries.withType(SharedLibraryBinary) {
                        assembler.args "SHARED1", "SHARED2"
                    }
                    binaries.withType(StaticLibraryBinary) {
                        assembler.args "STATIC1", "STATIC2"
                    }
                }
            }
        }

        then:
        SharedLibraryBinary sharedLib = project.binaries.testSharedLibrary
        sharedLib.tasks.withType(Assemble)*.name == ["assembleTestSharedLibraryTestAnotherOne", "assembleTestSharedLibraryTestAsm"]
        sharedLib.tasks.withType(Assemble).each { compile ->
            compile.toolChain == sharedLib.toolChain
            compile.assemblerArgs == ["ARG1", "ARG2", "SHARED1", "SHARED2"]
        }
        def sharedLinkTask = sharedLib.tasks.link
        sharedLinkTask TaskDependencyMatchers.dependsOn("assembleTestSharedLibraryTestAnotherOne", "assembleTestSharedLibraryTestAsm")

        and:
        StaticLibraryBinary staticLib = project.binaries.testStaticLibrary
        staticLib.tasks.withType(Assemble)*.name == ["assembleTestStaticLibraryTestAnotherOne", "assembleTestStaticLibraryTestAsm"]
        staticLib.tasks.withType(Assemble).each { compile ->
            compile.toolChain == sharedLib.toolChain
            compile.assemblerArgs == ["ARG1", "ARG2", "STATIC1", "STATIC2"]
        }
        def staticLibTask = staticLib.tasks.createStaticLib
        staticLibTask TaskDependencyMatchers.dependsOn("assembleTestStaticLibraryTestAnotherOne", "assembleTestStaticLibraryTestAsm")
    }

    def dsl(Closure closure) {
        closure.delegate = project
        closure()
        project.evaluate()
    }
}
