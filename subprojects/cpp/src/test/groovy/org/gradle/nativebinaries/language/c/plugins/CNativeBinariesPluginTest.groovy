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

package org.gradle.nativebinaries.language.c.plugins

import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.c.CSourceSet
import org.gradle.nativebinaries.ExecutableBinary
import org.gradle.nativebinaries.NativeBinary
import org.gradle.nativebinaries.SharedLibraryBinary
import org.gradle.nativebinaries.StaticLibraryBinary
import org.gradle.nativebinaries.language.c.tasks.CCompile
import org.gradle.util.TestUtil
import spock.lang.Specification

class CNativeBinariesPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    def "creates c source set with conventional locations for components"() {
        when:
        dsl {
            apply plugin: CNativeBinariesPlugin
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
        sourceSets.exe instanceof FunctionalSourceSet
        sourceSets.exe.c instanceof CSourceSet
        sourceSets.exe.c.source.srcDirs == [project.file("src/exe/c")] as Set
        sourceSets.exe.c.exportedHeaders.srcDirs == [project.file("src/exe/headers")] as Set
        project.executables.exe.source == [sourceSets.exe.c] as Set

        and:
        sourceSets.lib instanceof FunctionalSourceSet
        sourceSets.lib.c instanceof CSourceSet
        sourceSets.lib.c.source.srcDirs == [project.file("src/lib/c")] as Set
        sourceSets.lib.c.exportedHeaders.srcDirs == [project.file("src/lib/headers")] as Set
        project.libraries.lib.source == [sourceSets.lib.c] as Set
    }

    def "can configure source set locations"() {
        given:
        dsl {
            apply plugin: CNativeBinariesPlugin
            sources {
                exe {
                    c {
                        source {
                            srcDirs "d1", "d2"
                        }
                        exportedHeaders {
                            srcDirs "h1", "h2"
                        }
                    }
                }
                lib {
                    c {
                        source {
                            srcDirs "d3"
                        }
                        exportedHeaders {
                            srcDirs "h3"
                        }
                    }
                }
            }
        }

        expect:
        def sourceSets = project.sources
        with (sourceSets.exe.c) {
            source.srcDirs*.name == ["d1", "d2"]
            exportedHeaders.srcDirs*.name == ["h1", "h2"]
        }

        with (sourceSets.lib.c) {
            source.srcDirs*.name == ["d3"]
            exportedHeaders.srcDirs*.name == ["h3"]
        }
    }

    def "creates compile tasks for each executable source set"() {
        when:
        dsl {
            apply plugin: CNativeBinariesPlugin
            sources {
                test {
                    anotherOne(CSourceSet) {}
                }
            }
            executables {
                test {
                    binaries.all { NativeBinary binary ->
                        binary.cCompiler.define "NDEBUG"
                        binary.cCompiler.define "LEVEL", "1"
                        binary.cCompiler.args "ARG1", "ARG2"
                    }
                }
            }
        }

        then:
        ExecutableBinary binary = project.binaries.testExecutable
        binary.tasks.withType(CCompile)*.name == ["compileTestExecutableTestAnotherOne", "compileTestExecutableTestC"]

        and:
        binary.tasks.withType(CCompile).each { compile ->
            compile.toolChain == binary.toolChain
            compile.macros == [NDEBUG:null, LEVEL:"1"]
            compile.compilerArgs == ["ARG1", "ARG2"]
        }

        and:
        def linkTask = binary.tasks.link
        linkTask TaskDependencyMatchers.dependsOn("compileTestExecutableTestAnotherOne", "compileTestExecutableTestC")
    }

    def "creates compile task for each library source set"() {
        when:
        dsl {
            apply plugin: CNativeBinariesPlugin
            sources {
                test {
                    anotherOne(CSourceSet) {}
                }
            }
            libraries {
                test {
                    binaries.all {
                        cCompiler.define "NDEBUG"
                        cCompiler.define "LEVEL", "1"
                        cCompiler.args "ARG1", "ARG2"
                    }
                    binaries.withType(SharedLibraryBinary) {
                        cCompiler.args "SHARED1", "SHARED2"
                    }
                    binaries.withType(StaticLibraryBinary) {
                        cCompiler.args "STATIC1", "STATIC2"
                    }
                }
            }
        }

        then:
        SharedLibraryBinary sharedLib = project.binaries.testSharedLibrary
        sharedLib.tasks.withType(CCompile)*.name == ["compileTestSharedLibraryTestAnotherOne", "compileTestSharedLibraryTestC"]
        sharedLib.tasks.withType(CCompile).each { compile ->
            compile.toolChain == sharedLib.toolChain
            compile.macros == [NDEBUG:null, LEVEL:"1"]
            compile.compilerArgs == ["ARG1", "ARG2", "SHARED1", "SHARED2"]
        }
        def sharedLinkTask = sharedLib.tasks.link
        sharedLinkTask TaskDependencyMatchers.dependsOn("compileTestSharedLibraryTestAnotherOne", "compileTestSharedLibraryTestC")

        and:
        StaticLibraryBinary staticLib = project.binaries.testStaticLibrary
        staticLib.tasks.withType(CCompile)*.name == ["compileTestStaticLibraryTestAnotherOne", "compileTestStaticLibraryTestC"]
        staticLib.tasks.withType(CCompile).each { compile ->
            compile.toolChain == sharedLib.toolChain
            compile.macros == [NDEBUG:null, LEVEL:"1"]
            compile.compilerArgs == ["ARG1", "ARG2", "STATIC1", "STATIC2"]
        }
        def staticLibTask = staticLib.tasks.createStaticLib
        staticLibTask TaskDependencyMatchers.dependsOn("compileTestStaticLibraryTestAnotherOne", "compileTestStaticLibraryTestC")
    }

    def dsl(Closure closure) {
        closure.delegate = project
        closure()
        project.evaluate()
    }
}
