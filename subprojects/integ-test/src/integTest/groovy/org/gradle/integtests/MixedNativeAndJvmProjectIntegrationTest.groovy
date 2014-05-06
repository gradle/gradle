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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec;

public class MixedNativeAndJvmProjectIntegrationTest extends AbstractIntegrationSpec {

    def "can combine java, cpp-exe and cpp-lib plugins in a single project"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: "java"
            apply plugin: "cpp"

            nativeExecutables { mainExe {} }
            nativeLibraries { mainLib {} }

            task checkBinaries << {
                assert binaries.mainClasses instanceof ClassDirectoryBinary
                assert binaries.mainExeExecutable instanceof NativeExecutableBinary
                assert binaries.mainLibSharedLibrary instanceof SharedLibraryBinary
            }
"""
        expect:
        succeeds "checkBinaries"
    }

    def "can combine jvm and native components in the same project"() {
        buildFile << """
    apply plugin: 'native-component'
    apply plugin: 'jvm-component'

    nativeExecutables {
        nativeExe
    }
    nativeLibraries {
        nativeLib
    }
    jvmLibraries {
        jvmLib
    }

    task check << {
        assert softwareComponents.size() == 3
        assert softwareComponents.nativeExe instanceof NativeExecutable
        assert softwareComponents.nativeLib instanceof NativeLibrary
        assert softwareComponents.jvmLib instanceof JvmLibrary

        assert nativeExecutables as List == [softwareComponents.nativeExe]
        assert nativeLibraries as List == [softwareComponents.nativeLib]
        assert jvmLibraries as List == [softwareComponents.jvmLib]

        assert binaries.size() == 4
        binaries.jvmLibJar instanceof JvmLibraryBinary
        binaries.nativeExeExecutable instanceof NativeExecutableBinary
        binaries.nativeLibStaticLibrary instanceof StaticLibraryBinary
        binaries.nativeLibSharedLibrary instanceof SharedLibraryBinary
    }
"""
        expect:
        succeeds "check"
    }

    // TODO:DAZ Need to add some sources and actually build the binary outputs
    def "can build jvm and native components in the same project"() {
        buildFile << """
    apply plugin: 'native-component'
    apply plugin: 'jvm-component'

    nativeExecutables {
        nativeApp
    }
    nativeLibraries {
        nativeLib
    }
    jvmLibraries {
        jvmLib
    }
"""
        when:
        succeeds "jvmLibJar"

        then:
        executed ":createJvmLibJar", ":jvmLibJar"
        notExecuted  ":nativeAppExecutable", ":nativeLibStaticLibrary", ":nativeLibSharedLibrary"

        when:
        succeeds "nativeLibStaticLibrary"

        then:
        executed ":createNativeLibStaticLibrary", ":nativeLibStaticLibrary"
        notExecuted ":jvmLibJar", ":nativeAppExecutable", ":nativeLibSharedLibrary"

        when:
        succeeds  "nativeAppExecutable"

        then:
        executed ":linkNativeAppExecutable", ":nativeAppExecutable"
        notExecuted ":jvmLibJar", ":nativeLibStaticLibrary", ":nativeLibSharedLibrary"

        when:
        succeeds "assemble"

        then:
        executed ":jvmLibJar", ":nativeAppExecutable", ":nativeLibSharedLibrary", ":nativeLibStaticLibrary"
    }
}
