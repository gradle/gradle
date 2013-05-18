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

import spock.lang.Specification
import org.gradle.util.HelperUtil
import org.gradle.plugins.cpp.gpp.GppCompileSpec
import org.gradle.plugins.cpp.gpp.GppLibraryCompileSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.tasks.Sync
import org.gradle.util.Matchers

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
        executable.spec instanceof GppCompileSpec
        executable.outputFile == project.file("build/binaries/test")
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
        executable.spec instanceof GppCompileSpec
        executable.outputFile == project.file("build/binaries/test.exe")
    }

    def "creates tasks for each executable"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.executables {
            test
        }

        then:
        def compile = project.tasks['testExecutable']
        compile instanceof CppCompile
        compile.spec == project.executables.test.spec

        def install = project.tasks['installTestExecutable']
        install instanceof Sync
        install.destinationDir == project.file('build/install/testExecutable')
        install Matchers.dependsOn("testExecutable")
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
        def lib = project.libraries.test
        lib.spec instanceof GppLibraryCompileSpec
        lib.outputFile == project.file("build/binaries/libtest.dylib")
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
        def lib = project.libraries.test
        lib.spec instanceof GppLibraryCompileSpec
        lib.outputFile == project.file("build/binaries/libtest.so")
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
        def lib = project.libraries.test
        lib.spec instanceof GppLibraryCompileSpec
        lib.outputFile == project.file("build/binaries/test.dll")
    }

    def "creates tasks for each library"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.libraries {
            test
        }

        then:
        def compile = project.tasks['testSharedLibrary']
        compile instanceof CppCompile
        compile.spec == project.libraries.test.spec
    }
}
