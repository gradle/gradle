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

package org.gradle.nativebinaries.language.cpp.plugins

import org.gradle.api.tasks.TaskDependencyMatchers
import org.gradle.language.base.FunctionalSourceSet
import org.gradle.language.cpp.CppSourceSet
import org.gradle.nativebinaries.ExecutableBinary
import org.gradle.nativebinaries.NativeBinary
import org.gradle.nativebinaries.SharedLibraryBinary
import org.gradle.nativebinaries.StaticLibraryBinary
import org.gradle.nativebinaries.language.cpp.tasks.CppCompile
import org.gradle.util.TestUtil
import spock.lang.Specification

class CppNativeBinariesPluginTest extends Specification {
    final def project = TestUtil.createRootProject()

    def "creates cpp source set with conventional locations for components"() {
        when:
        dsl {
            apply plugin: CppNativeBinariesPlugin
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
        sourceSets.exe.cpp instanceof CppSourceSet
        sourceSets.exe.cpp.source.srcDirs == [project.file("src/exe/cpp")] as Set
        sourceSets.exe.cpp.exportedHeaders.srcDirs == [project.file("src/exe/headers")] as Set
        project.executables.exe.source == [sourceSets.exe.cpp] as Set

        and:
        sourceSets.lib instanceof FunctionalSourceSet
        sourceSets.lib.cpp instanceof CppSourceSet
        sourceSets.lib.cpp.source.srcDirs == [project.file("src/lib/cpp")] as Set
        sourceSets.lib.cpp.exportedHeaders.srcDirs == [project.file("src/lib/headers")] as Set
        project.libraries.lib.source == [sourceSets.lib.cpp] as Set
    }

    def "can configure source set locations"() {
        given:
        dsl {
            apply plugin: CppNativeBinariesPlugin
            sources {
                exe {
                    cpp {
                        source {
                            srcDirs "d1", "d2"
                        }
                        exportedHeaders {
                            srcDirs "h1", "h2"
                        }
                    }
                }
                lib {
                    cpp {
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
        with (sourceSets.exe.cpp) {
            source.srcDirs*.name == ["d1", "d2"]
            exportedHeaders.srcDirs*.name == ["h1", "h2"]
        }

        with (sourceSets.lib.cpp) {
            source.srcDirs*.name == ["d3"]
            exportedHeaders.srcDirs*.name == ["h3"]
        }
    }

    def "creates compile tasks for each executable source set"() {
        when:
        dsl {
            apply plugin: CppNativeBinariesPlugin
            sources {
                test {
                    anotherCpp(CppSourceSet) {}
                }
            }
            executables {
                test {
                    binaries.all { NativeBinary binary ->
                        binary.cppCompiler.define "NDEBUG"
                        binary.cppCompiler.define "LEVEL", "1"
                        binary.cppCompiler.args "ARG1", "ARG2"
                    }
                }
            }
        }

        then:
        ExecutableBinary binary = project.binaries.testExecutable
        binary.tasks.withType(CppCompile)*.name == ["compileTestExecutableTestAnotherCpp", "compileTestExecutableTestCpp"]

        and:
        binary.tasks.withType(CppCompile).each { compile ->
            compile.toolChain == binary.toolChain
            compile.macros == [NDEBUG:null, LEVEL:"1"]
            compile.compilerArgs == ["ARG1", "ARG2"]
        }

        and:
        def linkTask = binary.tasks.link
        linkTask TaskDependencyMatchers.dependsOn("compileTestExecutableTestAnotherCpp", "compileTestExecutableTestCpp")
    }

    def "creates compile task for each library source set"() {
        when:
        dsl {
            apply plugin: CppNativeBinariesPlugin
            sources {
                test {
                    anotherCpp(CppSourceSet) {}
                }
            }
            libraries {
                test {
                    binaries.all {
                        cppCompiler.define "NDEBUG"
                        cppCompiler.define "LEVEL", "1"
                        cppCompiler.args "ARG1", "ARG2"
                    }
                    binaries.withType(SharedLibraryBinary) {
                        cppCompiler.args "SHARED1", "SHARED2"
                    }
                    binaries.withType(StaticLibraryBinary) {
                        cppCompiler.args "STATIC1", "STATIC2"
                    }
                }
            }
        }

        then:
        SharedLibraryBinary sharedLib = project.binaries.testSharedLibrary
        sharedLib.tasks.withType(CppCompile)*.name == ["compileTestSharedLibraryTestAnotherCpp", "compileTestSharedLibraryTestCpp"]
        sharedLib.tasks.withType(CppCompile).each { compile ->
            compile.toolChain == sharedLib.toolChain
            compile.macros == [NDEBUG:null, LEVEL:"1"]
            compile.compilerArgs == ["ARG1", "ARG2", "SHARED1", "SHARED2"]
        }
        def sharedLinkTask = sharedLib.tasks.link
        sharedLinkTask TaskDependencyMatchers.dependsOn("compileTestSharedLibraryTestAnotherCpp", "compileTestSharedLibraryTestCpp")

        and:
        StaticLibraryBinary staticLib = project.binaries.testStaticLibrary
        staticLib.tasks.withType(CppCompile)*.name == ["compileTestStaticLibraryTestAnotherCpp", "compileTestStaticLibraryTestCpp"]
        staticLib.tasks.withType(CppCompile).each { compile ->
            compile.toolChain == sharedLib.toolChain
            compile.macros == [NDEBUG:null, LEVEL:"1"]
            compile.compilerArgs == ["ARG1", "ARG2", "STATIC1", "STATIC2"]
        }
        def staticLibTask = staticLib.tasks.createStaticLib
        staticLibTask TaskDependencyMatchers.dependsOn("compileTestStaticLibraryTestAnotherCpp", "compileTestStaticLibraryTestCpp")
    }

    def dsl(Closure closure) {
        closure.delegate = project
        closure()
        project.evaluate()
    }
}
