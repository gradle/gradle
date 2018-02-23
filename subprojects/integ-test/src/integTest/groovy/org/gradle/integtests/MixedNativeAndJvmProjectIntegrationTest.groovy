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

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain
import org.gradle.test.fixtures.archive.JarTestFixture

public class MixedNativeAndJvmProjectIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    def "can combine legacy java and cpp plugins in a single project"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
plugins {
    id 'java'
    id 'cpp'
}

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        mainExe(NativeExecutableSpec)
        mainLib(NativeLibrarySpec)
    }
    tasks {
        checkBinaries(Task) {
            def binaries = \$.binaries
            doLast {
                assert binaries.size() == 5
                assert binaries.main instanceof ClassDirectoryBinarySpec
                assert binaries.test instanceof ClassDirectoryBinarySpec
                assert binaries.mainExeExecutable instanceof NativeExecutableBinarySpec
                assert binaries.mainLibSharedLibrary instanceof SharedLibraryBinarySpec
                assert binaries.mainLibStaticLibrary instanceof StaticLibraryBinarySpec
            }
        }
    }
}
"""
        expect:
        succeeds "checkBinaries"
    }

    def "can combine jvm and native components in the same project"() {
        buildFile << """
plugins {
    id 'native-component'
    id 'jvm-component'
}

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        nativeExe(NativeExecutableSpec)
        nativeLib(NativeLibrarySpec)
        jvmLib(JvmLibrarySpec)
    }
    tasks {
        create("validate") {
            def components = \$.components
            def binaries = \$.binaries
            doLast {
                assert components.size() == 3
                assert components.nativeExe instanceof NativeExecutableSpec
                assert components.nativeLib instanceof NativeLibrarySpec
                assert components.jvmLib instanceof JvmLibrarySpec

                assert binaries.size() == 4
                assert binaries.jvmLibJar instanceof JarBinarySpec
                assert binaries.nativeExeExecutable instanceof NativeExecutableBinarySpec
                assert binaries.nativeLibStaticLibrary instanceof StaticLibraryBinarySpec
                assert binaries.nativeLibSharedLibrary instanceof SharedLibraryBinarySpec
            }
        }
    }
}
"""
        expect:
        succeeds "validate"
    }

    @RequiresInstalledToolChain
    def "build mixed components in one project"() {
        given:
        file("src/jvmLib/java/org/gradle/test/Test.java") << """
package org.gradle.test;

class Test {
    int val = 4;
    String name = "foo";
}
"""
        file("src/jvmLib/resources/test.txt") << "Here is a test resource"

        file("src/nativeApp/c/main.c") << """
#include <stdio.h>

int main () {
    printf("Hello world!");
    return 0;
}
"""

        and:
        buildFile << """
plugins {
    id 'native-component'
    id 'c'
    id 'jvm-component'
    id 'java-lang'
}

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        nativeApp(NativeExecutableSpec)
        jvmLib(JvmLibrarySpec)
    }
}
"""
        when:
        succeeds "jvmLibJar"

        then:
        executedAndNotSkipped ":compileJvmLibJarJvmLibJava", ":processJvmLibJarJvmLibResources", ":createJvmLibJar", ":jvmLibJar"
        notExecuted  ":nativeAppExecutable"

        when:
        succeeds  "nativeAppExecutable"

        then:
        executed ":compileNativeAppExecutableNativeAppC", ":linkNativeAppExecutable", ":nativeAppExecutable"
        notExecuted ":jvmLibJar"

        when:
        succeeds "assemble"

        then:
        executed ":jvmLibJar", ":nativeAppExecutable"

        and:
        new JarTestFixture(file("build/jars/jvmLib/jar/jvmLib.jar")).hasDescendants("org/gradle/test/Test.class", "test.txt");
        def nativeExeName = OperatingSystem.current().getExecutableName("nativeApp")
        file("build/exe/nativeApp/${nativeExeName}").assertExists()
    }
}
