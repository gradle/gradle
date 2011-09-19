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
package org.gradle.os

import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class OperatingSystemTest extends Specification {
    @Rule SetSystemProperties systemProperties = new SetSystemProperties()

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

    def "windows has case insensitive file system"() {
        def os = new OperatingSystem.Windows()

        expect:
        !os.fileSystem.caseSensitive
        !os.fileSystem.symlinkAware
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

    def "Mac OS X has case insensitive file system"() {
        def os = new OperatingSystem.MacOs()

        expect:
        !os.fileSystem.caseSensitive
        os.fileSystem.symlinkAware
    }

    def "uses os.name property to determine if solaris"() {
        System.properties['os.name'] = 'SunOS'

        expect:
        OperatingSystem.current() instanceof OperatingSystem.Solaris
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

    def "UNIX has case sensitive file system"() {
        def os = new OperatingSystem.Unix()

        expect:
        os.fileSystem.caseSensitive
        os.fileSystem.symlinkAware
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
}
