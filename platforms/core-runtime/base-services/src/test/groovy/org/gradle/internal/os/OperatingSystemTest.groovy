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

import org.gradle.internal.reflect.JavaReflectionUtil
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import java.lang.reflect.Field
import java.lang.reflect.Modifier

class OperatingSystemTest extends Specification {
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def setup() {
        OperatingSystem.resetCurrent()
    }

    def cleanup() {
        OperatingSystem.resetCurrent()
    }

    def cleanupSpec() {
        resetOperatingSystemClassStaticFields()
    }

    def "uses os.name property to determine OS name"() {
        given:
        System.properties['os.name'] = 'GradleOS 1.0'
        boolean resetStateSuccess = resetOperatingSystemClassStaticFields()

        expect:
        OperatingSystem.current().name == 'GradleOS 1.0' || !resetStateSuccess
    }

    def "uses os.version property to determine OS version"() {
        given:
        System.properties['os.version'] = '42'
        boolean resetStateSuccess = resetOperatingSystemClassStaticFields()

        expect:
        OperatingSystem.current().version == '42' || !resetStateSuccess
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
        os.getScriptName("a.exe") == "a.bat"
        os.getScriptName("a.b/c") == "a.b/c.bat"
        os.getScriptName("a.b\\c") == "a.b\\c.bat"
    }

    def "windows transforms executable names"() {
        def os = new OperatingSystem.Windows()

        expect:
        os.executableSuffix == ".exe"
        os.getExecutableName("a.exe") == "a.exe"
        os.getExecutableName("a.EXE") == "a.EXE"
        os.getExecutableName("a") == "a.exe"
        os.getExecutableName("a.bat") == "a.exe"
        os.getExecutableName("a.b/c") == "a.b/c.exe"
        os.getExecutableName("a.b\\c") == "a.b\\c.exe"
    }

    def "windows transforms shared library names"() {
        def os = new OperatingSystem.Windows()

        expect:
        os.sharedLibrarySuffix == ".dll"
        os.linkLibrarySuffix == ".lib"
        os.getSharedLibraryName("a.dll") == "a.dll"
        os.getSharedLibraryName("a.DLL") == "a.DLL"
        os.getSharedLibraryName("a") == "a.dll"
        os.getSharedLibraryName("a.lib") == "a.dll"
        os.getSharedLibraryName("a.b/c") == "a.b/c.dll"
        os.getSharedLibraryName("a.b\\c") == "a.b\\c.dll"
        os.getLinkLibraryName("a") == "a.lib"
        os.getLinkLibraryName("a.lib") == "a.lib"
    }

    def "windows transforms static library names"() {
        def os = new OperatingSystem.Windows()

        expect:
        os.staticLibrarySuffix == ".lib"
        os.getStaticLibraryName("a.lib") == "a.lib"
        os.getStaticLibraryName("a.LIB") == "a.LIB"
        os.getStaticLibraryName("a") == "a.lib"
        os.getStaticLibraryName("a.dll") == "a.lib"
        os.getStaticLibraryName("a.b/c") == "a.b/c.lib"
        os.getStaticLibraryName("a.b\\c") == "a.b\\c.lib"
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

    def "uses os.name property to determine if macOS"() {
        when:
        System.properties['os.name'] = 'Mac OS X'

        then:
        OperatingSystem.current() instanceof OperatingSystem.MacOs

        when:
        System.properties['os.name'] = 'Darwin'

        then:
        OperatingSystem.current() instanceof OperatingSystem.MacOs

        when:
        System.properties['os.name'] = 'osx'

        then:
        OperatingSystem.current() instanceof OperatingSystem.MacOs
    }

    def "macOS identifies itself correctly"() {
        def os = new OperatingSystem.MacOs()

        expect:
        !os.windows
        os.unix
        os.macOsX
    }

    def "uses os.name property to determine if sunos"() {
        when:
        System.properties['os.name'] = 'SunOS'

        then:
        OperatingSystem.current() instanceof OperatingSystem.Solaris

        when:
        System.properties['os.name'] = 'solaris'

        then:
        OperatingSystem.current() instanceof OperatingSystem.Solaris
    }

    def "uses os.name property to determine if linux"() {
        System.properties['os.name'] = 'Linux'

        expect:
        OperatingSystem.current() instanceof OperatingSystem.Linux
    }

    def "uses os.name property to determine if freebsd"() {
        System.properties['os.name'] = 'FreeBSD'

        expect:
        OperatingSystem.current() instanceof OperatingSystem.FreeBSD
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
        os.executableSuffix == ""
        os.getExecutableName("a.sh") == "a.sh"
        os.getExecutableName("a") == "a"
    }

    def "UNIX transforms shared library names"() {
        def os = new OperatingSystem.Unix()

        expect:
        os.sharedLibrarySuffix == ".so"
        os.linkLibrarySuffix == ".so"
        os.getSharedLibraryName("a.so") == "a.so"
        os.getSharedLibraryName("liba.so") == "liba.so"
        os.getSharedLibraryName("a") == "liba.so"
        os.getSharedLibraryName("lib1") == "liblib1.so"
        os.getSharedLibraryName("path/liba.so") == "path/liba.so"
        os.getSharedLibraryName("path/a") == "path/liba.so"
        os.getLinkLibraryName("a") == "liba.so"
    }

    def "UNIX transforms static library names"() {
        def os = new OperatingSystem.Unix()

        expect:
        os.staticLibrarySuffix == ".a"
        os.getStaticLibraryName("a.a") == "a.a"
        os.getStaticLibraryName("liba.a") == "liba.a"
        os.getStaticLibraryName("a") == "liba.a"
        os.getStaticLibraryName("lib1") == "liblib1.a"
        os.getStaticLibraryName("path/liba.a") == "path/liba.a"
        os.getStaticLibraryName("path/a") == "path/liba.a"
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
        given:
        System.properties['os.arch'] = arch
        def solaris = new OperatingSystem.Solaris()

        expect:
        solaris.nativePrefix == prefix

        where:
        [arch, prefix] << [['i386', 'sunos-x86'], ['x86', 'sunos-x86']]
    }

    def "unix uses prefix of i386 for 32bit intel"() {
        given:
        System.properties['os.name'] = 'unknown'
        System.properties['os.arch'] = arch
        def unix = new OperatingSystem.Unix()

        expect:
        unix.nativePrefix == prefix

        where:
        [arch, prefix] << [['i386', 'unknown-i386'], ['x86', 'unknown-i386']]
    }

    def "macOS uses same prefix for all architectures"() {
        def osx = new OperatingSystem.MacOs()

        expect:
        osx.nativePrefix == 'darwin'
    }

    def "macOS transforms shared library names"() {
        def os = new OperatingSystem.MacOs()

        expect:
        os.sharedLibrarySuffix == ".dylib"
        os.linkLibrarySuffix == ".dylib"
        os.getSharedLibraryName("a.dylib") == "a.dylib"
        os.getSharedLibraryName("liba.dylib") == "liba.dylib"
        os.getSharedLibraryName("a") == "liba.dylib"
        os.getSharedLibraryName("lib1") == "liblib1.dylib"
        os.getSharedLibraryName("path/liba.dylib") == "path/liba.dylib"
        os.getSharedLibraryName("path/a") == "path/liba.dylib"
        os.getLinkLibraryName("a") == "liba.dylib"
    }

    private static boolean resetOperatingSystemClassStaticFields() {
        try {
            OperatingSystem.getDeclaredFields()
                .findAll { Modifier.isStatic(it.modifiers) && Modifier.isFinal(it.modifiers) }
                .each { Field field ->
                if (OperatingSystem.isAssignableFrom(field.getType())) {
                    makeFinalFieldAccessibleForTesting(field)
                    field.set(null, JavaReflectionUtil.newInstance(field.getType()))
                }
            }
            return true
        } catch (Exception e) {
            System.err.println "Unable to make fields accessible on this JVM, error was:\n${e.message}"
            return false
        }
    }

    private static void makeFinalFieldAccessibleForTesting(Field field) {
        field.setAccessible(true)
        Field modifiersField = Field.class.getDeclaredField("modifiers")
        modifiersField.setAccessible(true)
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL)
    }
}
