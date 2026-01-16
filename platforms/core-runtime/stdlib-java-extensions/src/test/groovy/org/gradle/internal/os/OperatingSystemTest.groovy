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
