/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.os

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class OperatingSystemTest extends Specification {
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def "uses os.name property to determine OS name"() {
        System.properties['os.name'] = 'GradleOS 1.0'
        
        expect:
        OperatingSystem.current().name == 'GradleOS 1.0'
    }
    
    def "uses os.version property to determine OS version"() {
        System.properties['os.version'] = '42'
        
        expect:
        OperatingSystem.current().version == '42'
    }
    
    def "uses os.name property to determine if windows"() {
        System.properties['os.name'] = 'Windows 7'

        expect:
        OperatingSystem.current() instanceof OperatingSystem.Windows
    }

    def "windows identifies itself correctly"() {
        def os = new OperatingSystem.Windows()

        expect:
        os.windows
        !os.unix
        !os.macOsX
    }

    def "windows transforms script names"() {
        def os = new OperatingSystem.Windows()

        expect:
        os.getScriptName("a.bat") == "a.bat"
        os.getScriptName("a.BAT") == "a.BAT"
        os.getScriptName("a") == "a.bat"
    }

    def "windows transforms executable names"() {
        def os = new OperatingSystem.Windows()

        expect:
        os.getExecutableName("a.exe") == "a.exe"
        os.getExecutableName("a.EXE") == "a.EXE"
        os.getExecutableName("a") == "a.exe"
    }

    def "windows transforms shared library names"() {
        def os = new OperatingSystem.Windows()

        expect:
        os.getSharedLibraryName("a.dll") == "a.dll"
        os.getSharedLibraryName("a.DLL") == "a.DLL"
        os.getSharedLibraryName("a") == "a.dll"
    }

    def "windows searches for executable in path"() {
        def exe = tmpDir.createFile("bin/a.exe")
        tmpDir.createFile("bin2/a.exe")
        def os = new OperatingSystem.Windows() {
            @Override
            List<File> getPath() {
                return [tmpDir.file("bin"), tmpDir.file("bin2")]
            }
        }

        expect:
        os.findInPath("a.exe") == exe
        os.findInPath("a") == exe
        os.findInPath("unknown") == null
    }

    def "uses os.name property to determine if Mac OS X"() {
        when:
        System.properties['os.name'] = 'Mac OS X'

        then:
        OperatingSystem.current() instanceof OperatingSystem.MacOs

        when:
        System.properties['os.name'] = 'Darwin'

        then:
        OperatingSystem.current() instanceof OperatingSystem.MacOs
    }

    def "Mac OS X identifies itself correctly"() {
        def os = new OperatingSystem.MacOs()

        expect:
        !os.windows
        os.unix
        os.macOsX
    }

    def "uses os.name property to determine if solaris"() {
        System.properties['os.name'] = 'SunOS'

        expect:
        OperatingSystem.current() instanceof OperatingSystem.Solaris
    }

    def "uses os.name property to determine if linux"() {
        System.properties['os.name'] = 'Linux'

        expect:
        OperatingSystem.current() instanceof OperatingSystem.Linux
    }

    def "uses default implementation for other os"() {
        System.properties['os.name'] = 'unknown'

        expect:
        OperatingSystem.current() instanceof OperatingSystem.Unix
    }

    def "UNIX identifies itself correctly"() {
        def os = new OperatingSystem.Unix()

        expect:
        !os.windows
        os.unix
        !os.macOsX
    }

    def "UNIX does not transform script names"() {
        def os = new OperatingSystem.Unix()

        expect:
        os.getScriptName("a.sh") == "a.sh"
        os.getScriptName("a") == "a"
    }

    def "UNIX does not transforms executable names"() {
        def os = new OperatingSystem.Unix()

        expect:
        os.getExecutableName("a.sh") == "a.sh"
        os.getExecutableName("a") == "a"
    }

    def "UNIX transforms shared library names"() {
        def os = new OperatingSystem.Unix()

        expect:
        os.getSharedLibraryName("a.so") == "a.so"
        os.getSharedLibraryName("liba.so") == "liba.so"
        os.getSharedLibraryName("a") == "liba.so"
        os.getSharedLibraryName("lib1") == "liblib1.so"
        os.getSharedLibraryName("path/liba.so") == "path/liba.so"
        os.getSharedLibraryName("path/a") == "path/liba.so"
    }

    def "UNIX searches for executable in path"() {
        def exe = tmpDir.createFile("bin/a")
        tmpDir.createFile("bin2/a")
        def os = new OperatingSystem.Unix() {
            @Override
            List<File> getPath() {
                return [tmpDir.file("bin"), tmpDir.file("bin2")]
            }
        }

        expect:
        os.findInPath("a") == exe
        os.findInPath("unknown") == null
    }

    def "solaris uses prefix of x86 for 32bit intel"() {
        def solaris = new OperatingSystem.Solaris()

        when:
        System.properties['os.arch'] = 'i386'

        then:
        solaris.nativePrefix == 'sunos-x86'

        when:
        System.properties['os.arch'] = 'x86'

        then:
        solaris.nativePrefix == 'sunos-x86'
    }

    def "unix uses prefix of i386 for 32bit intel"() {
        def unix = new OperatingSystem.Unix()
        System.properties['os.name'] = 'unknown'

        when:
        System.properties['os.arch'] = 'x86'

        then:
        unix.nativePrefix == 'unknown-i386'

        when:
        System.properties['os.arch'] = 'i386'

        then:
        unix.nativePrefix == 'unknown-i386'
    }

    def "os x uses same prefix for all architectures"() {
        def osx = new OperatingSystem.MacOs()

        expect:
        osx.nativePrefix == 'darwin'
    }

    def "os x transforms shared library names"() {
        def os = new OperatingSystem.MacOs()

        expect:
        os.getSharedLibraryName("a.dylib") == "a.dylib"
        os.getSharedLibraryName("liba.dylib") == "liba.dylib"
        os.getSharedLibraryName("a") == "liba.dylib"
        os.getSharedLibraryName("lib1") == "liblib1.dylib"
        os.getSharedLibraryName("path/liba.dylib") == "path/liba.dylib"
        os.getSharedLibraryName("path/a") == "path/liba.dylib"
    }

}
