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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class ComponentReportIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << "rootProject.name = 'test'"
    }

    def "informs the user when project has no components defined"() {
        when:
        succeeds "components"

        then:
        output.contains(toPlatformLineSeparators("""
------------------------------------------------------------
Root project
------------------------------------------------------------

No components defined for this project.

Note: currently not all plugins register their components, so some components may not be visible here.

"""))
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
        output.contains(expected("""
No components defined for this project.

Additional source sets
----------------------
Java source 'main:java'
    src/main/java
Resources 'main:resources'
    src/main/resources
Java source 'test:java'
    src/test/java
Resources 'test:resources'
    src/test/resources

Additional binaries
-------------------
Classes 'main'
    build using task: :classes
    platform: target JDK 1.7
    tool chain: current JDK (1.7)
    classes dir: build/classes/main
    resources dir: build/resources/main
Classes 'test'
    build using task: :testClasses
    platform: target JDK 1.7
    tool chain: current JDK (1.7)
    classes dir: build/classes/test
    resources dir: build/resources/test

Note: currently not all plugins register their components, so some components may not be visible here.
"""))
    }

    def "shows details of Java library"() {
        given:
        buildFile << """
plugins {
    id 'jvm-component'
    id 'java-lang'
}

jvm {
    libraries {
        someLib
    }
}
"""
        when:
        succeeds "components"

        then:
        output.contains(expected("""
------------------------------------------------------------
Root project
------------------------------------------------------------

JVM library 'someLib'
---------------------

Source sets
    Resources 'someLib:resources'
        src/someLib/resources
    Java source 'someLib:java'
        src/someLib/java

Binaries
    Jar 'someLib:jar'
        build using task: :someLibJar
        platform: target JDK 1.7
        tool chain: current JDK (1.7)
        Jar file: build/jars/someLibJar/someLib.jar

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL
"""))
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
}

nativeRuntime {
    libraries {
        someLib
    }
}
"""
        when:
        succeeds "components"

        then:
        output.contains(expected("""
------------------------------------------------------------
Root project
------------------------------------------------------------

Native library 'someLib'
------------------------

Source sets
    C++ source 'someLib:cpp'
        src/someLib/cpp

Binaries
    Shared library 'someLib:sharedLibrary'
        build using task: :someLibSharedLibrary
        platform: current
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/libsomeLib.dylib
    Static library 'someLib:staticLibrary'
        build using task: :someLibStaticLibrary
        platform: current
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/libsomeLib.a

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL
"""))
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
}

nativeRuntime {
    libraries {
        someLib
    }
}
"""
        when:
        succeeds "components"

        then:
        output.contains(expected("""
------------------------------------------------------------
Root project
------------------------------------------------------------

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

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL
"""))
    }

    def "shows details of native C executable with test suite"() {
        given:
        buildFile << """
plugins {
    id 'c'
    id 'cunit'
}

model {
    toolChains {
        ${toolChain.buildScriptConfig}
    }
}

nativeRuntime {
    executables {
        someExe
    }
}
"""
        when:
        succeeds "components"

        then:
        output.contains(expected("""
------------------------------------------------------------
Root project
------------------------------------------------------------

Native executable 'someExe'
---------------------------

Source sets
    C source 'someExe:c'
        src/someExe/c

Binaries
    Executable 'someExe:executable'
        build using task: :someExeExecutable
        platform: current
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        executable file: build/binaries/someExeExecutable/someExe

Cunit test suite 'someExeTest'
------------------------------

Source sets
    C source 'someExeTest:cunitLauncher'
        build/src/someExeTest/cunitLauncher/c
    C source 'someExeTest:c'
        src/someExeTest/c

Binaries
    C unit exe 'someExeTest:cUnitExe'
        build using task: :someExeTestCUnitExe
        platform: current
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        executable file: build/binaries/someExeTestCUnitExe/someExeTest

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL
"""))
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
}

nativeRuntime {
    libraries {
        someLib
    }
}
"""
        when:
        succeeds "components"

        then:
        output.contains(expected("""
------------------------------------------------------------
Root project
------------------------------------------------------------

Native library 'someLib'
------------------------

Source sets
    C source 'someLib:c'
        src/someLib/c
    C++ source 'someLib:cpp'
        src/someLib/cpp
    Assembler source 'someLib:asm'
        src/someLib/asm

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

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL
"""))
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
}

jvm {
    libraries {
        jvmLib
    }
}
nativeRuntime {
    libraries {
        nativeLib
    }
}
"""
        when:
        succeeds "components"

        then:
        // TODO - flesh this out when languages are associated with correct component types
        output.contains(expected("""
------------------------------------------------------------
Root project
------------------------------------------------------------

JVM library 'jvmLib'
--------------------

Source sets
    Resources 'jvmLib:resources'
        src/jvmLib/resources
    Java source 'jvmLib:java'
        src/jvmLib/java

Binaries
    Jar 'jvmLib:jar'
        build using task: :jvmLibJar
        platform: target JDK 1.7
        tool chain: current JDK (1.7)
        Jar file: build/jars/jvmLibJar/jvmLib.jar

Native library 'nativeLib'
--------------------------

Source sets
    C++ source 'nativeLib:cpp'
        src/nativeLib/cpp
    C source 'nativeLib:c'
        src/nativeLib/c

Binaries
    Shared library 'nativeLib:sharedLibrary'
        build using task: :nativeLibSharedLibrary
        platform: current
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/nativeLibSharedLibrary/libnativeLib.dylib
    Static library 'nativeLib:staticLibrary'
        build using task: :nativeLibStaticLibrary
        platform: current
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/nativeLibStaticLibrary/libnativeLib.a

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL
"""))
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "shows details of jvm library with multiple targets"() {
        String current = JavaVersion.current();
        String target1 = JavaVersion.VERSION_1_5;
        String target2 = JavaVersion.VERSION_1_6;
        String target3 = current;
        given:
        buildFile << """
    apply plugin: 'jvm-component'
    apply plugin: 'java-lang'

    jvm {
        libraries {
            myLib {
                target java("$target1")
                target java("$target2")
                target java("$target3")
            }
        }
    }
"""
        when:
        succeeds "components"

        then:
        output.contains("""
------------------------------------------------------------
Root project
------------------------------------------------------------

JVM library 'myLib'
-------------------

Source sets
    Resources 'myLib:resources'
        src/myLib/resources
    Java source 'myLib:java'
        src/myLib/java

Binaries""")
    //order not guaranteed so check individual
    and:
    output.contains("""Jar 'myLib:jdk$target1:jar'
        build using task: :jdk${target1}MyLibJar
        platform: target JDK $target1
        tool chain: current JDK ($current)
        Jar file: build/jars/myLibJar/jdk$target1/myLib.jar""")

    and:
    output.contains("""Jar 'myLib:jdk$target2:jar'
        build using task: :jdk${target2}MyLibJar
        platform: target JDK $target2
        tool chain: current JDK ($current)
        Jar file: build/jars/myLibJar/jdk$target2/myLib.jar""")
    and:
    output.contains("""
    Jar 'myLib:jdk$target3:jar'
        build using task: :jdk${target3}MyLibJar
        platform: target JDK $target3
        tool chain: current JDK ($current)
        Jar file: build/jars/myLibJar/jdk$target3/myLib.jar""")
    and:
    output.contains("""
Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL
""")
    }

    String expected(String normalised) {
        return new ComponentReportOutputFormatter(toolChain).transform(normalised)
    }

    AvailableToolChains.InstalledToolChain getToolChain() {
        return AvailableToolChains.defaultToolChain
    }
}
