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

class ComponentReportIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << "rootProject.name = 'test'"
    }

    def "informs the user when project has no components defined"() {
        when:
        succeeds "components"

        then:
        output.contains("""
------------------------------------------------------------
Root project
------------------------------------------------------------

No components defined for this project.

Note: currently not all plugins register their components, so some components may not be visible here.

""")
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
        output.contains("""
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
Classes 'main' (not buildable)
    tool chain: current JDK (${JavaVersion.current()})
    build task: :classes
Classes 'test' (not buildable)
    tool chain: current JDK (${JavaVersion.current()})
    build task: :testClasses

Note: currently not all plugins register their components, so some components may not be visible here.
""")
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
        output.contains("""
------------------------------------------------------------
Root project
------------------------------------------------------------

JVM library 'someLib'
---------------------

Source sets
    Java source 'someLib:java'
        src/someLib/java
    Resources 'someLib:resources'
        src/someLib/resources

Binaries
    Jar 'someLib:jar'
        tool chain: current JDK (${JavaVersion.current()})
        Jar file: build/jars/someLibJar/someLib.jar
        build task: :someLibJar

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL
""")
    }

    def "shows details of native C++ library"() {
        given:
        buildFile << """
plugins {
    id 'cpp'
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
        output.contains("""
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
        platform: current
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/libsomeLib.dylib
        build task: :someLibSharedLibrary
    Static library 'someLib:staticLibrary'
        platform: current
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/libsomeLib.a
        build task: :someLibStaticLibrary

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL
""")
    }

    def "shows details of native C++ library that is not buildable"() {
        given:
        buildFile << """
plugins {
    id 'cpp'
}

model {
    platforms {
        solaris { operatingSystem 'solaris' }
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
        output.contains("""
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
        platform: solaris
        build type: debug
        flavor: default
        tool chain: unavailable
        shared library file: build/binaries/someLibSharedLibrary/libsomeLib.dylib
        build task: :someLibSharedLibrary
    Static library 'someLib:staticLibrary' (not buildable)
        platform: solaris
        build type: debug
        flavor: default
        tool chain: unavailable
        static library file: build/binaries/someLibStaticLibrary/libsomeLib.a
        build task: :someLibStaticLibrary

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL
""")
    }

    def "shows details of native C executable with test suite"() {
        given:
        buildFile << """
plugins {
    id 'c'
    id 'cunit'
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
        output.contains("""
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
        platform: current
        build type: debug
        flavor: default
        tool chain: Tool chain 'clang' (Clang)
        executable file: build/binaries/someExeExecutable/someExe
        build task: :someExeExecutable

Additional source sets
----------------------
C source 'someExeTest:c'
    src/someExeTest/c
C source 'someExeTest:cunit'
    src/someExeTest/cunit
C source 'someExeTest:cunitLauncher'
    src/someExeTest/cunitLauncher
    build/src/someExeTestCUnitLauncher/cunit

Additional binaries
-------------------
C unit exe 'someExeTest:cUnitExe'
    platform: current
    build type: debug
    flavor: default
    tool chain: Tool chain 'clang' (Clang)
    build task: :someExeTestCUnitExe

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL
""")
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
        output.contains("""
------------------------------------------------------------
Root project
------------------------------------------------------------

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
        platform: amd64
        build type: debug
        flavor: free
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/amd64Free/libsomeLib.dylib
        build task: :amd64FreeSomeLibSharedLibrary
    Static library 'someLib:amd64:free:staticLibrary'
        platform: amd64
        build type: debug
        flavor: free
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/amd64Free/libsomeLib.a
        build task: :amd64FreeSomeLibStaticLibrary
    Shared library 'someLib:amd64:paid:sharedLibrary'
        platform: amd64
        build type: debug
        flavor: paid
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/amd64Paid/libsomeLib.dylib
        build task: :amd64PaidSomeLibSharedLibrary
    Static library 'someLib:amd64:paid:staticLibrary'
        platform: amd64
        build type: debug
        flavor: paid
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/amd64Paid/libsomeLib.a
        build task: :amd64PaidSomeLibStaticLibrary
    Shared library 'someLib:i386:free:sharedLibrary'
        platform: i386
        build type: debug
        flavor: free
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/i386Free/libsomeLib.dylib
        build task: :i386FreeSomeLibSharedLibrary
    Static library 'someLib:i386:free:staticLibrary'
        platform: i386
        build type: debug
        flavor: free
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/i386Free/libsomeLib.a
        build task: :i386FreeSomeLibStaticLibrary
    Shared library 'someLib:i386:paid:sharedLibrary'
        platform: i386
        build type: debug
        flavor: paid
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/i386Paid/libsomeLib.dylib
        build task: :i386PaidSomeLibSharedLibrary
    Static library 'someLib:i386:paid:staticLibrary'
        platform: i386
        build type: debug
        flavor: paid
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/i386Paid/libsomeLib.a
        build task: :i386PaidSomeLibStaticLibrary

Note: currently not all plugins register their components, so some components may not be visible here.

BUILD SUCCESSFUL
""")
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
        output.contains("""
------------------------------------------------------------
Root project
------------------------------------------------------------

JVM library 'jvmLib'
--------------------
""")
        output.contains("""

Native library 'nativeLib'
--------------------------
""")
    }
}
