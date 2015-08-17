/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.components

import org.gradle.api.JavaVersion
import org.gradle.nativeplatform.fixtures.NativePlatformsTestFixture
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain

class DiagnosticsComponentReportIntegrationTest extends AbstractNativeComponentReportIntegrationTest {
    private JavaVersion currentJvm = JavaVersion.current()
    private String currentJavaName = "java" + currentJvm.majorVersion
    private String currentJava = "Java SE " + currentJvm.majorVersion
    private String currentJdk = String.format("JDK %s (%s)", currentJvm.majorVersion, currentJvm);
    private String currentNative = NativePlatformsTestFixture.defaultPlatformName

    @RequiresInstalledToolChain
    def "informs the user when project has no components defined"() {
        when:
        succeeds "components"

        then:
        outputMatches output, """
No components defined for this project.
"""
    }

    @RequiresInstalledToolChain
    def "shows details of multiple components"() {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
    id 'cpp'
    id 'c'
}

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        jvmLib(JvmLibrarySpec) {
            targetPlatform "$currentJavaName"
        }
        nativeLib(NativeLibrarySpec)
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
JVM library 'jvmLib'
--------------------

Source sets
    Java source 'jvmLib:java'
        srcDir: src/jvmLib/java
    JVM resources 'jvmLib:resources'
        srcDir: src/jvmLib/resources

Binaries
    Jar 'jvmLibJar'
        build using task: :jvmLibJar
        targetPlatform: $currentJava
        tool chain: $currentJdk
        Jar file: build/jars/jvmLibJar/jvmLib.jar

Native library 'nativeLib'
--------------------------

Source sets
    C source 'nativeLib:c'
        srcDir: src/nativeLib/c
    C++ source 'nativeLib:cpp'
        srcDir: src/nativeLib/cpp

Binaries
    Shared library 'nativeLib:sharedLibrary'
        build using task: :nativeLibSharedLibrary
        buildType: build type 'debug'
        flavor: flavor 'default'
        targetPlatform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/nativeLibSharedLibrary/libnativeLib.dylib
    Static library 'nativeLib:staticLibrary'
        build using task: :nativeLibStaticLibrary
        buildType: build type 'debug'
        flavor: flavor 'default'
        targetPlatform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/nativeLibStaticLibrary/libnativeLib.a
"""
    }

    def "shows an error when targeting a native platform from a jvm component"() {
        given:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'native-component'
    apply plugin: 'java-lang'

    model {
        platforms {
            i386 { architecture 'i386' }
        }
        components {
            myLib(JvmLibrarySpec) {
                targetPlatform "i386"
            }
        }
    }
"""
        when:
        fails "components"

        then:
        failure.assertHasCause("Invalid JavaPlatform: i386")
    }

    def "shows an error when targeting a jvm platform from a native component"() {
        given:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'native-component'
    apply plugin: 'java-lang'

    model {
        components {
            myLib(NativeLibrarySpec) {
                targetPlatform "java8"
            }
        }
    }
"""
        when:
        fails "components"

        then:
        failure.assertHasCause("Invalid NativePlatform: java8")
    }
}
