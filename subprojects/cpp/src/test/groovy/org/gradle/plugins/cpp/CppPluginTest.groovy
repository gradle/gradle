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

package org.gradle.plugins.cpp
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.tasks.Sync
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

    @Requires(TestPrecondition.UNIX)
    def "creates domain objects for executable on unix"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.executables {
            test
        }

        then:
        def executable = project.executables.test
        executable.outputFile == project.file("build/binaries/test")

        and:
        def executableBinary = project.binaries.testExecutable
        executableBinary.component == executable
    }

    @Requires(TestPrecondition.WINDOWS)
    def "creates domain objects for executable on windows"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.executables {
            test
        }

        then:
        def executable = project.executables.test
        executable.outputFile == project.file("build/binaries/test.exe")

        and:
        def executableBinary = project.binaries.testExecutable
        executableBinary.component == executable
    }

    def "creates tasks for each executable"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.executables {
            test {
                compilerArgs "ARG1", "ARG2"
                linkerArgs "LINK1", "LINK2"
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
        link.outputFile == project.executables.test.outputFile

        and:
        def install = project.tasks['installTestExecutable']
        install instanceof Sync
        install.destinationDir == project.file('build/install/testExecutable')
        install Matchers.dependsOn("testExecutable")

        and:
        project.executables.test.buildDependencies.getDependencies(null) == [link] as Set
    }

    @Requires(TestPrecondition.MAC_OS_X)
    def "creates domain objects for library on os x"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.libraries {
            test
        }

        then:
        def library = project.libraries.test
        library.outputFile == project.file("build/binaries/libtest.dylib")

        and:
        def sharedLibraryBinary = project.binaries.testSharedLibrary
        sharedLibraryBinary.component == library
    }

    @Requires(TestPrecondition.LINUX)
    def "creates domain objects for library on linux"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.libraries {
            test
        }

        then:
        def library = project.libraries.test
        library.outputFile == project.file("build/binaries/libtest.so")

        and:
        def sharedLibraryBinary = project.binaries.testSharedLibrary
        sharedLibraryBinary.component == library
    }

    @Requires(TestPrecondition.WINDOWS)
    def "creates domain objects for library on windows"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.libraries {
            test
        }

        then:
        def library = project.libraries.test
        library.outputFile == project.file("build/binaries/test.dll")

        and:
        def sharedLibraryBinary = project.binaries.testSharedLibrary
        sharedLibraryBinary.component == library
    }

    def "creates tasks for each library"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.libraries {
            test {
                compilerArgs "ARG1", "ARG2"
                linkerArgs "LINK1", "LINK2"
            }
        }

        then:
        def compile = project.tasks['compileTestSharedLibrary']
        compile instanceof CppCompile
        compile.compilerArgs == ["ARG1", "ARG2"]

        and:
        def link = project.tasks['testSharedLibrary']
        link instanceof LinkSharedLibrary
        link.linkerArgs == ["LINK1", "LINK2"]
        link.outputFile == project.libraries.test.outputFile

        and:
        def staticLink = project.tasks['testStaticLibrary']
        staticLink instanceof LinkStaticLibrary
        staticLink.linkerArgs == ["LINK1", "LINK2"]
        staticLink.outputFile == project.binaries.testStaticLibrary.outputFile

        and:
        project.libraries.test.buildDependencies.getDependencies(null) == [link, staticLink] as Set
    }
}
