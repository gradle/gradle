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
package org.gradle.nativeplatform

import org.gradle.api.reporting.components.AbstractNativeComponentReportIntegrationTest
import org.gradle.nativeplatform.fixtures.NativePlatformsTestFixture
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain

class PlatformNativeComponentReportIntegrationTest extends AbstractNativeComponentReportIntegrationTest {
    private String currentNative = NativePlatformsTestFixture.defaultPlatformName

    @RequiresInstalledToolChain
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
        someLib(NativeLibrarySpec) {
            binaries.withType(StaticLibraryBinarySpec) {
                sources {
                    moreCpp(CppSourceSet)
                }
            }
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
        srcDir: src/someLib/cpp

Binaries
    Shared library 'someLib:sharedLibrary'
        build using task: :someLibSharedLibrary
        buildType: build type 'debug'
        flavor: flavor 'default'
        targetPlatform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/libsomeLib.dylib
    Static library 'someLib:staticLibrary'
        build using task: :someLibStaticLibrary
        buildType: build type 'debug'
        flavor: flavor 'default'
        targetPlatform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/libsomeLib.a
        source sets:
            C++ source 'someLib:moreCpp'
                No source directories
"""
    }

    @RequiresInstalledToolChain
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
        anotherLib(NativeLibrarySpec) {
            binaries.withType(StaticLibraryBinarySpec) { buildable = false }
        }
    }
}
"""
        when:
        succeeds "components"

        then:
        outputMatches output, """
Native library 'anotherLib'
---------------------------

Source sets
    C++ source 'anotherLib:cpp'
        srcDir: src/anotherLib/cpp

Binaries
    Shared library 'anotherLib:sharedLibrary'
        build using task: :anotherLibSharedLibrary
        buildType: build type 'debug'
        flavor: flavor 'default'
        targetPlatform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/anotherLibSharedLibrary/libanotherLib.dylib
    Static library 'anotherLib:staticLibrary' (not buildable)
        build using task: :anotherLibStaticLibrary
        buildType: build type 'debug'
        flavor: flavor 'default'
        targetPlatform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/anotherLibStaticLibrary/libanotherLib.a
        Disabled by user

Native library 'someLib'
------------------------

Source sets
    C++ source 'someLib:cpp'
        srcDir: src/someLib/cpp

Binaries
    Shared library 'someLib:sharedLibrary' (not buildable)
        build using task: :someLibSharedLibrary
        buildType: build type 'debug'
        flavor: flavor 'default'
        targetPlatform: platform 'windows'
        tool chain: unavailable
        shared library file: build/binaries/someLibSharedLibrary/someLib.dll
        No tool chain is available to build for platform 'windows':
          - ${toolChain.instanceDisplayName}: Don't know how to build for platform 'windows'.
    Static library 'someLib:staticLibrary' (not buildable)
        build using task: :someLibStaticLibrary
        buildType: build type 'debug'
        flavor: flavor 'default'
        targetPlatform: platform 'windows'
        tool chain: unavailable
        static library file: build/binaries/someLibStaticLibrary/someLib.lib
        No tool chain is available to build for platform 'windows':
          - ${toolChain.instanceDisplayName}: Don't know how to build for platform 'windows'.
"""
    }

    @RequiresInstalledToolChain
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
            targetPlatform "i386"
            targetPlatform "amd64"
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
        srcDir: src/someLib/asm
    C source 'someLib:c'
        srcDir: src/someLib/c
    C++ source 'someLib:cpp'
        srcDir: src/someLib/cpp

Binaries
    Shared library 'someLib:amd64:free:sharedLibrary'
        build using task: :amd64FreeSomeLibSharedLibrary
        buildType: build type 'debug'
        flavor: flavor 'free'
        targetPlatform: platform 'amd64'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/amd64Free/libsomeLib.dylib
    Static library 'someLib:amd64:free:staticLibrary'
        build using task: :amd64FreeSomeLibStaticLibrary
        buildType: build type 'debug'
        flavor: flavor 'free'
        targetPlatform: platform 'amd64'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/amd64Free/libsomeLib.a
    Shared library 'someLib:amd64:paid:sharedLibrary'
        build using task: :amd64PaidSomeLibSharedLibrary
        buildType: build type 'debug'
        flavor: flavor 'paid'
        targetPlatform: platform 'amd64'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/amd64Paid/libsomeLib.dylib
    Static library 'someLib:amd64:paid:staticLibrary'
        build using task: :amd64PaidSomeLibStaticLibrary
        buildType: build type 'debug'
        flavor: flavor 'paid'
        targetPlatform: platform 'amd64'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/amd64Paid/libsomeLib.a
    Shared library 'someLib:i386:free:sharedLibrary'
        build using task: :i386FreeSomeLibSharedLibrary
        buildType: build type 'debug'
        flavor: flavor 'free'
        targetPlatform: platform 'i386'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/i386Free/libsomeLib.dylib
    Static library 'someLib:i386:free:staticLibrary'
        build using task: :i386FreeSomeLibStaticLibrary
        buildType: build type 'debug'
        flavor: flavor 'free'
        targetPlatform: platform 'i386'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/i386Free/libsomeLib.a
    Shared library 'someLib:i386:paid:sharedLibrary'
        build using task: :i386PaidSomeLibSharedLibrary
        buildType: build type 'debug'
        flavor: flavor 'paid'
        targetPlatform: platform 'i386'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/binaries/someLibSharedLibrary/i386Paid/libsomeLib.dylib
    Static library 'someLib:i386:paid:staticLibrary'
        build using task: :i386PaidSomeLibStaticLibrary
        buildType: build type 'debug'
        flavor: flavor 'paid'
        targetPlatform: platform 'i386'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/binaries/someLibStaticLibrary/i386Paid/libsomeLib.a
"""
    }
}
