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
import org.gradle.nativeplatform.platform.internal.NativePlatforms
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class ComponentReportIntegrationTest extends AbstractComponentReportIntegrationTest {
    private JavaVersion currentJvm = JavaVersion.current()
    private String currentJava = "java" + currentJvm.majorVersion
    private String currentJdk = String.format("JDK %s (%s)", currentJvm.majorVersion, currentJvm);
    private String currentNative = NativePlatforms.defaultPlatformName

    def "informs the user when project has no components defined"() {
        when:
        succeeds "components"

        then:
        outputMatches output, """
No components defined for this project.
"""
    }

    def "shows details of legacy Java project"() {
        given:
        buildFile << """
plugins {
    id 'java'
}
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
No components defined for this project.

Additional source sets
----------------------
Java source 'main:java'
    src/main/java
JVM resources 'main:resources'
    src/main/resources
Java source 'test:java'
    src/test/java
JVM resources 'test:resources'
    src/test/resources

Additional binaries
-------------------
Classes 'main'
    build using task: :classes
    platform: $currentJava
    tool chain: $currentJdk
    classes dir: build/classes/main
    resources dir: build/resources/main
Classes 'test'
    build using task: :testClasses
    platform: $currentJava
    tool chain: $currentJdk
    classes dir: build/classes/test
    resources dir: build/resources/test
"""
    }

    def "shows details of Java library"() {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        someLib(JvmLibrarySpec)
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
JVM library 'someLib'
---------------------

Source sets
    Java source 'someLib:java'
        src/someLib/java
    JVM resources 'someLib:resources'
        src/someLib/resources

Binaries
    Jar 'someLibJar'
        build using task: :someLibJar
        platform: $currentJava
        tool chain: $currentJdk
        Jar file: build/jars/someLibJar/someLib.jar
"""
    }

    def "shows details of native C++ library"() {
        given:
        buildFile << """
plugins {
    id 'cpp'
}

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        someLib(NativeLibrarySpec)
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
Native library 'someLib'
------------------------

Source sets
    C++ source 'someLib:cpp'
        src/someLib/cpp

Binaries
    Shared library 'someLib:sharedLibrary'
        build using task: :someLibSharedLibrary
        platform: $currentNative
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/libsomeLib.dylib
    Static library 'someLib:staticLibrary'
        build using task: :someLibStaticLibrary
        platform: $currentNative
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/libsomeLib.a
"""
    }

    def "shows details of native C++ library that is not buildable"() {
        given:
        buildFile << """
plugins {
    id 'cpp'
}

model {
    platforms {
        windows { operatingSystem 'windows'; architecture 'sparc' }
    }
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    components {
        someLib(NativeLibrarySpec) {
            targetPlatform "windows"
        }
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
Native library 'someLib'
------------------------

Source sets
    C++ source 'someLib:cpp'
        src/someLib/cpp

Binaries
    Shared library 'someLib:sharedLibrary' (not buildable)
        build using task: :someLibSharedLibrary
        platform: windows
        build type: debug
        flavor: default
        tool chain: unavailable
        shared library file: build/binaries/someLibSharedLibrary/someLib.dll
    Static library 'someLib:staticLibrary' (not buildable)
        build using task: :someLibStaticLibrary
        platform: windows
        build type: debug
        flavor: default
        tool chain: unavailable
        static library file: build/binaries/someLibStaticLibrary/someLib.lib
"""
    }

    def "shows details of polyglot native library with multiple variants"() {
        given:
        buildFile << """
plugins {
    id 'c'
    id 'cpp'
    id 'assembler'
}

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
    platforms {
        i386 { architecture 'i386' }
        amd64 { architecture 'amd64' }
    }
    flavors {
        free
        paid
    }
    components {
        someLib(NativeLibrarySpec) {
            targetPlatform "i386", "amd64"
        }
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
Native library 'someLib'
------------------------

Source sets
    Assembler source 'someLib:asm'
        src/someLib/asm
    C source 'someLib:c'
        src/someLib/c
    C++ source 'someLib:cpp'
        src/someLib/cpp

Binaries
    Shared library 'someLib:amd64:free:sharedLibrary'
        build using task: :amd64FreeSomeLibSharedLibrary
        platform: amd64
        build type: debug
        flavor: free
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/amd64Free/libsomeLib.dylib
    Static library 'someLib:amd64:free:staticLibrary'
        build using task: :amd64FreeSomeLibStaticLibrary
        platform: amd64
        build type: debug
        flavor: free
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/amd64Free/libsomeLib.a
    Shared library 'someLib:amd64:paid:sharedLibrary'
        build using task: :amd64PaidSomeLibSharedLibrary
        platform: amd64
        build type: debug
        flavor: paid
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/amd64Paid/libsomeLib.dylib
    Static library 'someLib:amd64:paid:staticLibrary'
        build using task: :amd64PaidSomeLibStaticLibrary
        platform: amd64
        build type: debug
        flavor: paid
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/amd64Paid/libsomeLib.a
    Shared library 'someLib:i386:free:sharedLibrary'
        build using task: :i386FreeSomeLibSharedLibrary
        platform: i386
        build type: debug
        flavor: free
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/i386Free/libsomeLib.dylib
    Static library 'someLib:i386:free:staticLibrary'
        build using task: :i386FreeSomeLibStaticLibrary
        platform: i386
        build type: debug
        flavor: free
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/i386Free/libsomeLib.a
    Shared library 'someLib:i386:paid:sharedLibrary'
        build using task: :i386PaidSomeLibSharedLibrary
        platform: i386
        build type: debug
        flavor: paid
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/i386Paid/libsomeLib.dylib
    Static library 'someLib:i386:paid:staticLibrary'
        build using task: :i386PaidSomeLibStaticLibrary
        platform: i386
        build type: debug
        flavor: paid
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/i386Paid/libsomeLib.a
"""
    }

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
            targetPlatform "$currentJava"
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
        src/jvmLib/java
    JVM resources 'jvmLib:resources'
        src/jvmLib/resources

Binaries
    Jar 'jvmLibJar'
        build using task: :jvmLibJar
        platform: ${currentJava}
        tool chain: $currentJdk
        Jar file: build/jars/jvmLibJar/jvmLib.jar

Native library 'nativeLib'
--------------------------

Source sets
    C source 'nativeLib:c'
        src/nativeLib/c
    C++ source 'nativeLib:cpp'
        src/nativeLib/cpp

Binaries
    Shared library 'nativeLib:sharedLibrary'
        build using task: :nativeLibSharedLibrary
        platform: $currentNative
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/nativeLibSharedLibrary/libnativeLib.dylib
    Static library 'nativeLib:staticLibrary'
        build using task: :nativeLibStaticLibrary
        platform: $currentNative
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/nativeLibStaticLibrary/libnativeLib.a
"""
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "shows details of jvm library with multiple targets"() {
        given:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    model {
        components {
            myLib(JvmLibrarySpec) {
                targetPlatform "java5", "java6", "java7"
            }
        }
    }
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
JVM library 'myLib'
-------------------

Source sets
    Java source 'myLib:java'
        src/myLib/java
    JVM resources 'myLib:resources'
        src/myLib/resources

Binaries
    Jar 'java5MyLibJar'
        build using task: :java5MyLibJar
        platform: java5
        tool chain: $currentJdk
        Jar file: build/jars/java5MyLibJar/myLib.jar
    Jar 'java6MyLibJar'
        build using task: :java6MyLibJar
        platform: java6
        tool chain: $currentJdk
        Jar file: build/jars/java6MyLibJar/myLib.jar
    Jar 'java7MyLibJar'
        build using task: :java7MyLibJar
        platform: java7
        tool chain: $currentJdk
        Jar file: build/jars/java7MyLibJar/myLib.jar
"""
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "shows which jvm libraries are buildable"() {
        given:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    model {
        components {
            myLib(JvmLibrarySpec) {
                targetPlatform "java5", "java6", "java9"
            }
        }
    }
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
JVM library 'myLib'
-------------------

Source sets
    Java source 'myLib:java'
        src/myLib/java
    JVM resources 'myLib:resources'
        src/myLib/resources

Binaries
    Jar 'java5MyLibJar'
        build using task: :java5MyLibJar
        platform: java5
        tool chain: $currentJdk
        Jar file: build/jars/java5MyLibJar/myLib.jar
    Jar 'java6MyLibJar'
        build using task: :java6MyLibJar
        platform: java6
        tool chain: $currentJdk
        Jar file: build/jars/java6MyLibJar/myLib.jar
    Jar 'java9MyLibJar' (not buildable)
        build using task: :java9MyLibJar
        platform: java9
        tool chain: $currentJdk
        Jar file: build/jars/java9MyLibJar/myLib.jar
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
