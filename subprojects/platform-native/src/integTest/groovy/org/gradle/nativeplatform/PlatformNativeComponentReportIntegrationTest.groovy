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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.RequiresInstalledToolChain

class PlatformNativeComponentReportIntegrationTest extends AbstractNativeComponentReportIntegrationTest {
    //TODO:HH Test rendering of a NativeExecutableSpec
    // Should display installation directory

    @RequiresInstalledToolChain
    @ToBeFixedForConfigurationCache(because = ":components")
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
        outputMatches """
Native library 'someLib'
------------------------

Source sets
    C++ source 'someLib:cpp'
        srcDir: src/someLib/cpp

Binaries
    Shared library 'someLib:sharedLibrary'
        build using task: :someLibSharedLibrary
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/libs/someLib/shared/libsomeLib.dylib
    Static library 'someLib:staticLibrary'
        build using task: :someLibStaticLibrary
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/libs/someLib/static/libsomeLib.a
        source sets:
            C++ source 'someLib:staticLibrary:moreCpp'
                srcDir: src/staticLibrary/moreCpp
"""
    }

    @RequiresInstalledToolChain
    @ToBeFixedForConfigurationCache(because = ":components")
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
        outputMatches """
Native library 'anotherLib'
---------------------------

Source sets
    C++ source 'anotherLib:cpp'
        srcDir: src/anotherLib/cpp

Binaries
    Shared library 'anotherLib:sharedLibrary'
        build using task: :anotherLibSharedLibrary
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/libs/anotherLib/shared/libanotherLib.dylib
    Static library 'anotherLib:staticLibrary' (not buildable)
        build using task: :anotherLibStaticLibrary
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform '$currentNative'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/libs/anotherLib/static/libanotherLib.a
        Disabled by user

Native library 'someLib'
------------------------

Source sets
    C++ source 'someLib:cpp'
        srcDir: src/someLib/cpp

Binaries
    Shared library 'someLib:sharedLibrary' (not buildable)
        build using task: :someLibSharedLibrary
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform 'windows'
        tool chain: unavailable
        shared library file: build/libs/someLib/shared/someLib.dll
        No tool chain is available to build for platform 'windows':
          - ${toolChain.instanceDisplayName}:
              - Don't know how to build for platform 'windows'.
    Static library 'someLib:staticLibrary' (not buildable)
        build using task: :someLibStaticLibrary
        build type: build type 'debug'
        flavor: flavor 'default'
        target platform: platform 'windows'
        tool chain: unavailable
        static library file: build/libs/someLib/static/someLib.lib
        No tool chain is available to build for platform 'windows':
          - ${toolChain.instanceDisplayName}:
              - Don't know how to build for platform 'windows'.
"""
    }

    @RequiresInstalledToolChain
    @ToBeFixedForConfigurationCache(because = ":components")
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
        outputMatches """
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
        build using task: :someLibAmd64FreeSharedLibrary
        build type: build type 'debug'
        flavor: flavor 'free'
        target platform: platform 'amd64'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/libs/someLib/shared/amd64/free/libsomeLib.dylib
    Static library 'someLib:amd64:free:staticLibrary'
        build using task: :someLibAmd64FreeStaticLibrary
        build type: build type 'debug'
        flavor: flavor 'free'
        target platform: platform 'amd64'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/libs/someLib/static/amd64/free/libsomeLib.a
    Shared library 'someLib:amd64:paid:sharedLibrary'
        build using task: :someLibAmd64PaidSharedLibrary
        build type: build type 'debug'
        flavor: flavor 'paid'
        target platform: platform 'amd64'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/libs/someLib/shared/amd64/paid/libsomeLib.dylib
    Static library 'someLib:amd64:paid:staticLibrary'
        build using task: :someLibAmd64PaidStaticLibrary
        build type: build type 'debug'
        flavor: flavor 'paid'
        target platform: platform 'amd64'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/libs/someLib/static/amd64/paid/libsomeLib.a
    Shared library 'someLib:i386:free:sharedLibrary'
        build using task: :someLibI386FreeSharedLibrary
        build type: build type 'debug'
        flavor: flavor 'free'
        target platform: platform 'i386'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/libs/someLib/shared/i386/free/libsomeLib.dylib
    Static library 'someLib:i386:free:staticLibrary'
        build using task: :someLibI386FreeStaticLibrary
        build type: build type 'debug'
        flavor: flavor 'free'
        target platform: platform 'i386'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/libs/someLib/static/i386/free/libsomeLib.a
    Shared library 'someLib:i386:paid:sharedLibrary'
        build using task: :someLibI386PaidSharedLibrary
        build type: build type 'debug'
        flavor: flavor 'paid'
        target platform: platform 'i386'
        tool chain: Tool chain 'clang' (Clang)
        shared library file: build/libs/someLib/shared/i386/paid/libsomeLib.dylib
    Static library 'someLib:i386:paid:staticLibrary'
        build using task: :someLibI386PaidStaticLibrary
        build type: build type 'debug'
        flavor: flavor 'paid'
        target platform: platform 'i386'
        tool chain: Tool chain 'clang' (Clang)
        static library file: build/libs/someLib/static/i386/paid/libsomeLib.a
"""
    }
}
