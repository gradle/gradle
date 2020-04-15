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

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain

class DiagnosticsComponentReportIntegrationTest extends AbstractNativeComponentReportIntegrationTest {

    private void expectJavaLanguagePluginDeprecationWarnings() {
        executer.expectDocumentedDeprecationWarning("The jvm-component plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The java-lang plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
        executer.expectDocumentedDeprecationWarning("The jvm-resources plugin has been deprecated. This is scheduled to be removed in Gradle 7.0. " +
            "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_6.html#upgrading_jvm_plugins")
    }

    @RequiresInstalledToolChain
    @ToBeFixedForInstantExecution(because = ":components")
    def "informs the user when project has no components defined"() {
        when:
        succeeds "components"

        then:
        outputMatches """
No components defined for this project.
"""
    }

    @RequiresInstalledToolChain
    @ToBeFixedForInstantExecution(because = ":components")
    def "shows details of multiple components"() {
        expectJavaLanguagePluginDeprecationWarnings()

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
        outputMatches """
JVM library 'jvmLib'
--------------------

Source sets
    Java source 'jvmLib:java'
        srcDir: src/jvmLib/java
    JVM resources 'jvmLib:resources'
        srcDir: src/jvmLib/resources

Binaries
    Jar 'jvmLib:jar'
        build using task: :jvmLibJar
        target platform: $currentJava
        tool chain: $currentJdk
        classes dir: build/classes/jvmLib/jar
        resources dir: build/resources/jvmLib/jar
        API Jar file: build/jars/jvmLib/jar/api/jvmLib.jar
        Jar file: build/jars/jvmLib/jar/jvmLib.jar

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
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/libs/nativeLib/shared/libnativeLib.dylib
    Static library 'nativeLib:staticLibrary'
        build using task: :nativeLibStaticLibrary
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/libs/nativeLib/static/libnativeLib.a
"""
    }

    def "shows an error when targeting a native platform from a jvm component"() {
        expectJavaLanguagePluginDeprecationWarnings()

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
        expectJavaLanguagePluginDeprecationWarnings()

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
