/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.platform;

import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule;
import spock.lang.Specification;

class PlatformBinaryResolverTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "windows transforms script names"() {
        def resolver = PlatformBinaryResolver.forOs(OperatingSystem.WINDOWS)

        expect:
        resolver.getScriptName("a.bat") == "a.bat"
        resolver.getScriptName("a.BAT") == "a.BAT"
        resolver.getScriptName("a") == "a.bat"
        resolver.getScriptName("a.exe") == "a.bat"
        resolver.getScriptName("a.b/c") == "a.b/c.bat"
        resolver.getScriptName("a.b\\c") == "a.b\\c.bat"
    }

    def "windows transforms executable names"() {
        def resolver = PlatformBinaryResolver.forOs(OperatingSystem.WINDOWS)

        expect:
        resolver.getExecutableSuffix() == ".exe"
        resolver.getExecutableName("a.exe") == "a.exe"
        resolver.getExecutableName("a.EXE") == "a.EXE"
        resolver.getExecutableName("a") == "a.exe"
        resolver.getExecutableName("a.bat") == "a.exe"
        resolver.getExecutableName("a.b/c") == "a.b/c.exe"
        resolver.getExecutableName("a.b\\c") == "a.b\\c.exe"
    }

    def "windows transforms shared library names"() {
        def resolver = PlatformBinaryResolver.forOs(OperatingSystem.WINDOWS)

        expect:
        resolver.getSharedLibrarySuffix() == ".dll"
        resolver.getLinkLibrarySuffix() == ".lib"
        resolver.getSharedLibraryName("a.dll") == "a.dll"
        resolver.getSharedLibraryName("a.DLL") == "a.DLL"
        resolver.getSharedLibraryName("a") == "a.dll"
        resolver.getSharedLibraryName("a.lib") == "a.dll"
        resolver.getSharedLibraryName("a.b/c") == "a.b/c.dll"
        resolver.getSharedLibraryName("a.b\\c") == "a.b\\c.dll"
        resolver.getLinkLibraryName("a") == "a.lib"
        resolver.getLinkLibraryName("a.lib") == "a.lib"
    }

    def "windows transforms static library names"() {
        def resolver = PlatformBinaryResolver.forOs(OperatingSystem.WINDOWS)

        expect:
        resolver.getStaticLibrarySuffix() == ".lib"
        resolver.getStaticLibraryName("a.lib") == "a.lib"
        resolver.getStaticLibraryName("a.LIB") == "a.LIB"
        resolver.getStaticLibraryName("a") == "a.lib"
        resolver.getStaticLibraryName("a.dll") == "a.lib"
        resolver.getStaticLibraryName("a.b/c") == "a.b/c.lib"
        resolver.getStaticLibraryName("a.b\\c") == "a.b\\c.lib"
    }

    def "windows searches for executable in path"() {
        given:
        def exe = tmpDir.createFile("bin/a.exe")
        tmpDir.createFile("bin2/a.exe")
        def os = Spy(OperatingSystem.WINDOWS)
        os.getPath() >> [tmpDir.file("bin"), tmpDir.file("bin2")]
        def resolver = PlatformBinaryResolver.forOs(os)

        expect:
        resolver.findExecutableInPath("a.exe") == exe
        resolver.findExecutableInPath("a") == exe
        resolver.findExecutableInPath("unknown") == null
    }

    def "UNIX does not transform script names"() {
        def resolver = PlatformBinaryResolver.forOs(OperatingSystem.UNIX)

        expect:
        resolver.getScriptName("a.sh") == "a.sh"
        resolver.getScriptName("a") == "a"
    }

    def "UNIX does not transform executable names"() {
        def resolver = PlatformBinaryResolver.forOs(OperatingSystem.UNIX)

        expect:
        resolver.getExecutableSuffix() == ""
        resolver.getExecutableName("a.sh") == "a.sh"
        resolver.getExecutableName("a") == "a"
    }

    def "UNIX transforms shared library names"() {
        def resolver = PlatformBinaryResolver.forOs(OperatingSystem.UNIX)

        expect:
        resolver.getSharedLibrarySuffix() == ".so"
        resolver.getLinkLibrarySuffix() == ".so"
        resolver.getSharedLibraryName("a.so") == "a.so"
        resolver.getSharedLibraryName("liba.so") == "liba.so"
        resolver.getSharedLibraryName("a") == "liba.so"
        resolver.getSharedLibraryName("lib1") == "liblib1.so"
        resolver.getSharedLibraryName("path/liba.so") == "path/liba.so"
        resolver.getSharedLibraryName("path/a") == "path/liba.so"
        resolver.getLinkLibraryName("a") == "liba.so"
    }

    def "UNIX transforms static library names"() {
        def resolver = PlatformBinaryResolver.forOs(OperatingSystem.UNIX)

        expect:
        resolver.getStaticLibrarySuffix() == ".a"
        resolver.getStaticLibraryName("a.a") == "a.a"
        resolver.getStaticLibraryName("liba.a") == "liba.a"
        resolver.getStaticLibraryName("a") == "liba.a"
        resolver.getStaticLibraryName("lib1") == "liblib1.a"
        resolver.getStaticLibraryName("path/liba.a") == "path/liba.a"
        resolver.getStaticLibraryName("path/a") == "path/liba.a"
    }

    def "UNIX searches for executable in path"() {
        given:
        def exe = tmpDir.createFile("bin/a")
        tmpDir.createFile("bin2/a")
        def os = Spy(OperatingSystem.UNIX)
        os.getPath() >> [tmpDir.file("bin"), tmpDir.file("bin2")]
        def resolver = PlatformBinaryResolver.forOs(os)

        expect:
        resolver.findExecutableInPath("a") == exe
        resolver.findExecutableInPath("unknown") == null
    }

    def "macOS transforms shared library names"() {
        def resolver = PlatformBinaryResolver.forOs(OperatingSystem.MAC_OS)

        expect:
        resolver.getSharedLibrarySuffix() == ".dylib"
        resolver.getLinkLibrarySuffix() == ".dylib"
        resolver.getSharedLibraryName("a.dylib") == "a.dylib"
        resolver.getSharedLibraryName("liba.dylib") == "liba.dylib"
        resolver.getSharedLibraryName("a") == "liba.dylib"
        resolver.getSharedLibraryName("lib1") == "liblib1.dylib"
        resolver.getSharedLibraryName("path/liba.dylib") == "path/liba.dylib"
        resolver.getSharedLibraryName("path/a") == "path/liba.dylib"
        resolver.getLinkLibraryName("a") == "liba.dylib"
    }
}
