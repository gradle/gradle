/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativecode.language.cpp.plugins

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.nativecode.base.tasks.AssembleStaticLibrary
import org.gradle.nativecode.base.tasks.InstallExecutable
import org.gradle.nativecode.base.tasks.LinkExecutable
import org.gradle.nativecode.base.tasks.LinkSharedLibrary
import org.gradle.nativecode.language.cpp.CppSourceSet
import org.gradle.nativecode.language.cpp.tasks.CppCompile
import org.gradle.util.HelperUtil
import org.gradle.util.Matchers
import spock.lang.Specification

class CppPluginTest extends Specification {
    final def project = HelperUtil.createRootProject()

    def "extensions are available"() {
        given:
        dsl {
            apply plugin: CppPlugin
        }

        expect:
        project.cpp instanceof CppExtension
        project.executables instanceof NamedDomainObjectContainer
        project.libraries instanceof NamedDomainObjectContainer
    }

    def "gcc and visual cpp adapters are available"() {
        given:
        dsl {
            apply plugin: CppPlugin
        }

        expect:
        project.toolChains*.name == ['gcc', 'visualCpp']
        project.toolChains.searchOrder*.name == ['visualCpp', 'gcc']
    }

    def "can create some cpp source sets"() {
        given:
        dsl {
            apply plugin: CppPlugin
            cpp {
                sourceSets {
                    s1 {}
                    s2 {}
                }
            }
        }

        expect:
        def sourceSets = project.cpp.sourceSets
        sourceSets.size() == 2
        sourceSets*.name == ["s1", "s2"]
        sourceSets.s1 instanceof CppSourceSet
    }

    def "configure source sets"() {
        given:
        dsl {
            apply plugin: CppPlugin
            cpp {
                sourceSets {
                    ss1 {
                        source {
                            srcDirs "d1", "d2"
                        }
                        exportedHeaders {
                            srcDirs "h1", "h2"
                        }
                    }
                    ss2 {
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
        def sourceSets = project.cpp.sourceSets
        def ss1 = sourceSets.ss1
        def ss2 = sourceSets.ss2

        // cpp dir automatically added by convention
        ss1.source.srcDirs*.name == ["cpp", "d1", "d2"]
        ss2.source.srcDirs*.name == ["cpp", "d3"]

        // headers dir automatically added by convention
        ss1.exportedHeaders.srcDirs*.name == ["headers", "h1", "h2"]
        ss2.exportedHeaders.srcDirs*.name == ["headers", "h3"]
    }

    def "creates tasks for each executable"() {
        given:
        dsl {
            apply plugin: CppPlugin
            executables {
                test {
                    binaries.all {
                        define "NDEBUG"
                        compilerArgs "ARG1", "ARG2"
                        linkerArgs "LINK1", "LINK2"
                    }
                }
            }
        }

        expect:
        def binary = project.binaries.testExecutable

        def compile = project.tasks.compileTestExecutable
        compile instanceof CppCompile
        compile.toolChain == binary.toolChain
        compile.macros == ["NDEBUG"]
        compile.compilerArgs == ["ARG1", "ARG2"]

        and:
        def link = project.tasks.linkTestExecutable
        link instanceof LinkExecutable
        link.toolChain == binary.toolChain
        link.linkerArgs == ["LINK1", "LINK2"]
        link.outputFile == project.binaries.testExecutable.outputFile
        link Matchers.dependsOn("compileTestExecutable")

        and:
        def lifecycle = project.tasks.testExecutable
        lifecycle Matchers.dependsOn("linkTestExecutable")

        and:
        def install = project.tasks.installTestExecutable
        install instanceof InstallExecutable
        install.destinationDir == project.file('build/install/testExecutable')
        install.executable == project.binaries.testExecutable.outputFile
        install.libs.files.empty
        install Matchers.dependsOn("testExecutable")

        and:
        project.binaries.testExecutable.buildDependencies.getDependencies(null) == [lifecycle] as Set
    }

    def "creates tasks for each library"() {
        given:
        dsl {
            apply plugin: CppPlugin

            libraries {
                test {
                    binaries.all {
                        define "NDEBUG"
                        compilerArgs "ARG1", "ARG2"
                        linkerArgs "LINK1", "LINK2"
                    }
                }
            }
        }

        expect:
        def sharedLib = project.binaries.testSharedLibrary
        def staticLib = project.binaries.testStaticLibrary

        def sharedCompile = project.tasks.compileTestSharedLibrary
        sharedCompile instanceof CppCompile
        sharedCompile.toolChain == sharedLib.toolChain
        sharedCompile.macros == ["NDEBUG"]
        sharedCompile.compilerArgs == ["ARG1", "ARG2"]

        and:
        def link = project.tasks.linkTestSharedLibrary
        link instanceof LinkSharedLibrary
        link.toolChain == sharedLib.toolChain
        link.linkerArgs == ["LINK1", "LINK2"]
        link.outputFile == sharedLib.outputFile
        link Matchers.dependsOn("compileTestSharedLibrary")

        and:
        def sharedLibraryTask = project.tasks.testSharedLibrary
        sharedLibraryTask Matchers.dependsOn("linkTestSharedLibrary")

        and:
        def staticCompile = project.tasks.compileTestStaticLibrary
        staticCompile instanceof CppCompile
        staticCompile.toolChain == staticLib.toolChain
        staticCompile.macros == ["NDEBUG"]
        staticCompile.compilerArgs == ["ARG1", "ARG2"]

        and:
        def staticLink = project.tasks.assembleTestStaticLibrary
        staticLink instanceof AssembleStaticLibrary
        staticLink.toolChain == staticLib.toolChain
        staticLink.outputFile == staticLib.outputFile
        staticLink Matchers.dependsOn("compileTestStaticLibrary")

        and:
        def staticLibraryTask = project.tasks.testStaticLibrary
        staticLibraryTask Matchers.dependsOn("assembleTestStaticLibrary")

        and:
        sharedLib.buildDependencies.getDependencies(null) == [sharedLibraryTask] as Set
        staticLib.buildDependencies.getDependencies(null) == [staticLibraryTask] as Set
    }

    def dsl(Closure closure) {
        closure.delegate = project
        closure()
        project.evaluate()
    }
}
