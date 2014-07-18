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
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.cpp.fixtures.AvailableToolChains
import spock.lang.Ignore

import static org.gradle.util.TextUtil.toPlatformLineSeparators

@Ignore
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
        output.contains(toPlatformLineSeparators("""
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
    build using task: :classes
    tool chain: current JDK (${JavaVersion.current()})
    classes dir: build/classes/main
    resources dir: build/resources/main
Classes 'test' (not buildable)
    build using task: :testClasses
    tool chain: current JDK (${JavaVersion.current()})
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
        output.contains(toPlatformLineSeparators("""
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
        build using task: :someLibJar
        tool chain: current JDK (${JavaVersion.current()})
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

nativeRuntime {
    libraries {
        someLib
    }
}
"""
        when:
        succeeds "components"

        then:
        output.contains(toPlatformLineSeparators("""
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
        tool chain: ${toolChainDisplayName}
        shared library file: ${sharedLibrary('build/binaries/someLibSharedLibrary/someLib')}
    Static library 'someLib:staticLibrary'
        build using task: :someLibStaticLibrary
        platform: current
        build type: debug
        flavor: default
        tool chain: ${toolChainDisplayName}
        static library file: ${staticLibrary('build/binaries/someLibStaticLibrary/someLib')}

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
        output.contains(toPlatformLineSeparators("""
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
        platform: solaris
        build type: debug
        flavor: default
        tool chain: unavailable
        shared library file: ${sharedLibrary('build/binaries/someLibSharedLibrary/someLib')}
    Static library 'someLib:staticLibrary' (not buildable)
        build using task: :someLibStaticLibrary
        platform: solaris
        build type: debug
        flavor: default
        tool chain: unavailable
        static library file: ${staticLibrary('build/binaries/someLibStaticLibrary/someLib')}

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

nativeRuntime {
    executables {
        someExe
    }
}
"""
        when:
        succeeds "components"

        then:
        output.contains(toPlatformLineSeparators("""
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
        tool chain: ${toolChainDisplayName}
        executable file: ${executable('build/binaries/someExeExecutable/someExe')}

Additional source sets
----------------------
C source 'someExeTest:c'
    src/someExeTest/c
C source 'someExeTest:cunitLauncher'
    build/src/someExeTest/cunitLauncher/c

Additional binaries
-------------------
C unit exe 'someExeTest:cUnitExe'
    build using task: :someExeTestCUnitExe
    platform: current
    build type: debug
    flavor: default
    tool chain: ${toolChainDisplayName}
    executable file: ${executable('build/binaries/someExeTestCUnitExe/someExeTest')}

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
        output.contains(toPlatformLineSeparators("""
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
        build using task: :amd64FreeSomeLibSharedLibrary
        platform: amd64
        build type: debug
        flavor: free
        tool chain: ${toolChainDisplayName}
        shared library file: ${sharedLibrary('build/binaries/someLibSharedLibrary/amd64Free/someLib')}
    Static library 'someLib:amd64:free:staticLibrary'
        build using task: :amd64FreeSomeLibStaticLibrary
        platform: amd64
        build type: debug
        flavor: free
        tool chain: ${toolChainDisplayName}
        static library file: ${staticLibrary('build/binaries/someLibStaticLibrary/amd64Free/someLib')}
    Shared library 'someLib:amd64:paid:sharedLibrary'
        build using task: :amd64PaidSomeLibSharedLibrary
        platform: amd64
        build type: debug
        flavor: paid
        tool chain: ${toolChainDisplayName}
        shared library file: ${sharedLibrary('build/binaries/someLibSharedLibrary/amd64Paid/someLib')}
    Static library 'someLib:amd64:paid:staticLibrary'
        build using task: :amd64PaidSomeLibStaticLibrary
        platform: amd64
        build type: debug
        flavor: paid
        tool chain: ${toolChainDisplayName}
        static library file: ${staticLibrary('build/binaries/someLibStaticLibrary/amd64Paid/someLib')}
    Shared library 'someLib:i386:free:sharedLibrary'
        build using task: :i386FreeSomeLibSharedLibrary
        platform: i386
        build type: debug
        flavor: free
        tool chain: ${toolChainDisplayName}
        shared library file: ${sharedLibrary('build/binaries/someLibSharedLibrary/i386Free/someLib')}
    Static library 'someLib:i386:free:staticLibrary'
        build using task: :i386FreeSomeLibStaticLibrary
        platform: i386
        build type: debug
        flavor: free
        tool chain: ${toolChainDisplayName}
        static library file: ${staticLibrary('build/binaries/someLibStaticLibrary/i386Free/someLib')}
    Shared library 'someLib:i386:paid:sharedLibrary'
        build using task: :i386PaidSomeLibSharedLibrary
        platform: i386
        build type: debug
        flavor: paid
        tool chain: ${toolChainDisplayName}
        shared library file: ${sharedLibrary('build/binaries/someLibSharedLibrary/i386Paid/someLib')}
    Static library 'someLib:i386:paid:staticLibrary'
        build using task: :i386PaidSomeLibStaticLibrary
        platform: i386
        build type: debug
        flavor: paid
        tool chain: ${toolChainDisplayName}
        static library file: ${staticLibrary('build/binaries/someLibStaticLibrary/i386Paid/someLib')}

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
        output.contains(toPlatformLineSeparators("""
------------------------------------------------------------
Root project
------------------------------------------------------------

JVM library 'jvmLib'
--------------------
"""))
        output.contains(toPlatformLineSeparators("""

Native library 'nativeLib'
--------------------------
"""))
    }

    String getToolChainDisplayName() {
        return toolChain.instanceDisplayName
    }

    AvailableToolChains.InstalledToolChain getToolChain() {
        return AvailableToolChains.toolChains.find { it.available }
    }

    String executable(String path) {
        return OperatingSystem.current().getExecutableName(path)
    }

    String sharedLibrary(String path) {
        return OperatingSystem.current().getSharedLibraryName(path)
    }

    String staticLibrary(String path) {
        return OperatingSystem.current().getStaticLibraryName(path)
    }
}
