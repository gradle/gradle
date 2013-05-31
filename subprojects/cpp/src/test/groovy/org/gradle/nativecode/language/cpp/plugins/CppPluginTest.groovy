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
import org.gradle.api.tasks.Sync
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativecode.base.tasks.CreateStaticLibrary
import org.gradle.nativecode.base.tasks.LinkExecutable
import org.gradle.nativecode.base.tasks.LinkSharedLibrary
import org.gradle.nativecode.language.cpp.CppSourceSet
import org.gradle.nativecode.language.cpp.tasks.CppCompile
import org.gradle.util.HelperUtil
import org.gradle.util.Matchers
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Specification

class CppPluginTest extends Specification {
    final def project = HelperUtil.createRootProject()

    def "extensions are available"() {
        given:
        project.plugins.apply(CppPlugin)

        expect:
        project.cpp instanceof CppExtension
        project.executables instanceof NamedDomainObjectContainer
        project.libraries instanceof NamedDomainObjectContainer
    }

    @Requires(TestPrecondition.WINDOWS)
    def "gcc and visual cpp adapters are available on windows"() {
        given:
        project.plugins.apply(CppPlugin)

        expect:
        project.compilers.collect { it.name } == ['gpp', 'visualCpp']
        project.compilers.searchOrder.collect { it.name } == ['visualCpp', 'gpp']
    }

    @Requires(TestPrecondition.UNIX)
    def "gcc adapter is available on unix"() {
        given:
        project.plugins.apply(CppPlugin)

        expect:
        project.compilers.collect { it.name } == ['gpp']
        project.compilers.searchOrder.collect { it.name } == ['gpp']
    }

    def "can create some cpp source sets"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.cpp {
            sourceSets {
                s1 {}
                s2 {}
            }
        }

        then:
        def sourceSets = project.cpp.sourceSets
        sourceSets.size() == 2
        sourceSets*.name == ["s1", "s2"]
        sourceSets.s1 instanceof CppSourceSet
    }

    def "configure source sets"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.cpp {
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

        then:
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

    def "creates domain objects for executable"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.executables {
            test {
                binaries.all {
                    compilerArgs "ARG1", "ARG2"
                    linkerArgs "LINK1", "LINK2"
                }
            }
        }

        then:
        def executable = project.executables.test

        and:
        def executableBinary = project.binaries.testExecutable
        executableBinary.component == executable
        executableBinary.compilerArgs == ["ARG1", "ARG2"]
        executableBinary.linkerArgs == ["LINK1", "LINK2"]
        executableBinary.outputFile == project.file("build/binaries/${OperatingSystem.current().getExecutableName('test')}")

        and:
        executable.binaries.contains executableBinary
    }

    def "creates tasks for each executable"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.executables {
            test {
                binaries.all {
                    compilerArgs "ARG1", "ARG2"
                    linkerArgs "LINK1", "LINK2"
                }
            }
        }

        then:
        def compile = project.tasks['compileTestExecutable']
        compile instanceof CppCompile
        compile.compilerArgs == ["ARG1", "ARG2"]

        and:
        def link = project.tasks['testExecutable']
        link instanceof LinkExecutable
        link.linkerArgs == ["LINK1", "LINK2"]
        link.outputFile == project.binaries.testExecutable.outputFile
        link Matchers.dependsOn("compileTestExecutable")

        and:
        def install = project.tasks['installTestExecutable']
        install instanceof Sync
        install.destinationDir == project.file('build/install/testExecutable')
        install Matchers.dependsOn("testExecutable")

        and:
        project.binaries.testExecutable.buildDependencies.getDependencies(null) == [link] as Set
    }

    def "creates domain objects for library"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.libraries {
            test
        }

        then:
        final sharedLibName = OperatingSystem.current().getSharedLibraryName("test")
        final staticLibName = OperatingSystem.current().getStaticLibraryName("test")
        def library = project.libraries.test

        and:
        def sharedLibraryBinary = project.binaries.testSharedLibrary
        sharedLibraryBinary.outputFile == project.file("build/binaries/$sharedLibName")
        sharedLibraryBinary.component == project.libraries.test

        and:
        def staticLibraryBinary = project.binaries.testStaticLibrary
        staticLibraryBinary.outputFile == project.file("build/binaries/$staticLibName")
        staticLibraryBinary.component == project.libraries.test

        and:
        library.binaries.contains(sharedLibraryBinary)
        library.binaries.contains(staticLibraryBinary)
    }

    def "creates tasks for each library"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.libraries {
            test {
                binaries.all {
                    compilerArgs "ARG1", "ARG2"
                    linkerArgs "LINK1", "LINK2"
                }
            }
        }

        then:
        def sharedCompile = project.tasks['compileTestSharedLibrary']
        sharedCompile instanceof CppCompile
        sharedCompile.compilerArgs == ["ARG1", "ARG2"]

        and:
        def link = project.tasks['testSharedLibrary']
        link instanceof LinkSharedLibrary
        link.linkerArgs == ["LINK1", "LINK2"]
        link.outputFile == project.binaries.testSharedLibrary.outputFile
        link Matchers.dependsOn("compileTestSharedLibrary")

        and:
        def staticCompile = project.tasks['compileTestStaticLibrary']
        staticCompile instanceof CppCompile
        staticCompile.compilerArgs == ["ARG1", "ARG2"]

        and:
        def staticLink = project.tasks['testStaticLibrary']
        staticLink instanceof CreateStaticLibrary
        staticLink.outputFile == project.binaries.testStaticLibrary.outputFile
        staticLink Matchers.dependsOn("compileTestStaticLibrary")

        and:
        project.binaries.testSharedLibrary.buildDependencies.getDependencies(null) == [link] as Set
        project.binaries.testStaticLibrary.buildDependencies.getDependencies(null) == [staticLink] as Set
    }
}
