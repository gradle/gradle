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
import org.gradle.plugins.binaries.tasks.Compile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class CppPluginTest extends Specification {
    final def project = HelperUtil.createRootProject()

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
        executable.spec.outputFile == project.file("build/binaries/test")
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
        executable.spec.outputFile == project.file("build/binaries/test.exe")
    }

    def "creates tasks for each executable"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.executables {
            test
        }

        then:
        def task = project.tasks['compileTest']
        task instanceof Compile
        task.spec == project.executables.test.spec
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
        lib.spec.outputFile == project.file("build/binaries/libtest.dylib")
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
        lib.spec.outputFile == project.file("build/binaries/libtest.so")
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
        lib.spec.outputFile == project.file("build/binaries/test.dll")
    }

    def "creates tasks for each library"() {
        given:
        project.plugins.apply(CppPlugin)

        when:
        project.libraries {
            test
        }

        then:
        def task = project.tasks['compileTest']
        task instanceof Compile
        task.spec == project.libraries.test.spec
    }
}
