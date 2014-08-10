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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.archive.JarTestFixture

public class MixedNativeAndJvmProjectIntegrationTest extends AbstractIntegrationSpec {

    def "can combine legacy java and cpp plugins in a single project"() {
        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            apply plugin: "java"
            apply plugin: "cpp"

            nativeRuntime {
                executables {
                    mainExe
                }
                libraries {
                    mainLib
                }
            }

            task checkBinaries << {
                assert binaries.mainClasses instanceof ClassDirectoryBinarySpec
                assert binaries.mainExeExecutable instanceof NativeExecutableBinarySpec
                assert binaries.mainLibSharedLibrary instanceof SharedLibraryBinarySpec
            }
"""
        expect:
        succeeds "checkBinaries"
    }

    def "can combine jvm and native components in the same project"() {
        buildFile << """
    apply plugin: 'native-component'
    apply plugin: 'jvm-component'

    nativeRuntime {
        executables {
            nativeExe
        }
        libraries {
            nativeLib
        }
    }

    jvm {
        libraries {
            jvmLib
        }
    }

    task check << {
        assert componentSpecs.size() == 3
        assert componentSpecs.nativeExe instanceof NativeExecutableSpec
        assert componentSpecs.nativeLib instanceof NativeLibrarySpec
        assert componentSpecs.jvmLib instanceof JvmLibrarySpec

        assert nativeRuntime.executables as List == [componentSpecs.nativeExe]
        assert nativeRuntime.libraries as List == [componentSpecs.nativeLib]
        assert jvm.libraries as List == [componentSpecs.jvmLib]

        assert binaries.size() == 4
        assert binaries.jvmLibJar instanceof JarBinarySpec
        assert binaries.nativeExeExecutable instanceof NativeExecutableBinarySpec
        assert binaries.nativeLibStaticLibrary instanceof StaticLibraryBinarySpec
        assert binaries.nativeLibSharedLibrary instanceof SharedLibraryBinarySpec
    }
"""
        expect:
        succeeds "check"
    }

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
    apply plugin: 'native-component'
    apply plugin: 'c'
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    nativeRuntime {
        executables {
            nativeApp
        }
    }
    jvm {
        libraries {
            jvmLib
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
        new JarTestFixture(file("build/jars/jvmLibJar/jvmLib.jar")).hasDescendants("org/gradle/test/Test.class", "test.txt");
        def nativeExeName = OperatingSystem.current().getExecutableName("nativeApp")
        file("build/binaries/nativeAppExecutable/${nativeExeName}").assertExists()
    }
}
