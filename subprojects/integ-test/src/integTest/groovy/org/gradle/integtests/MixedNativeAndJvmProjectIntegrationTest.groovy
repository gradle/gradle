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

            executables { main {} }
            libraries { main {} }

            task checkBinaries << {
                assert binaries.mainClasses instanceof ClassDirectoryBinary
                assert binaries.mainExecutable instanceof NativeExecutableBinary
                assert binaries.mainSharedLibrary instanceof SharedLibraryBinary
            }
"""
        expect:
        succeeds "checkBinaries"
    }

    def "can combine JvmLibrary and NativeLibrary components in the same project"() {
        buildFile << """
    apply plugin: 'native-component'
    apply plugin: 'jvm-component'

    libraries {
        nativeLib(NativeLibrary)
        jvmLib(JvmLibrary)
    }

    task check << {
        assert libraries.size() == 2
        assert libraries.nativeLib instanceof NativeLibrary
        assert libraries.jvmLib instanceof JvmLibrary

        assert binaries.size() == 3
        binaries.jvmLibJar instanceof JvmLibraryBinary
        binaries.nativeLibStaticLibrary instanceof StaticLibraryBinary
        binaries.nativeLibSharedLibrary instanceof SharedLibraryBinary
    }
"""
        expect:
        succeeds "check"
    }
}
