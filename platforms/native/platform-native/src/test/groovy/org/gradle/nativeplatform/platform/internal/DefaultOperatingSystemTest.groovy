/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.platform.internal

import org.gradle.internal.os.OperatingSystem
import spock.lang.Specification

class DefaultOperatingSystemTest extends Specification {
    def "has useful string representation"() {
        def os = new DefaultOperatingSystem("sunos")

        expect:
        os.toString() == "operating system 'sunos'"
        os.displayName == "operating system 'sunos'"
        os.internalOs == OperatingSystem.SOLARIS
    }

    def "recognises key operating systems"() {
        def os = new DefaultOperatingSystem(name)

        expect:
        os.name == name
        os.internalOs == internalOs

        where:
        name      | internalOs
        "windows" | OperatingSystem.WINDOWS
        "osx"     | OperatingSystem.MAC_OS
        "linux"   | OperatingSystem.LINUX
        "sunos"   | OperatingSystem.SOLARIS
        "solaris" | OperatingSystem.SOLARIS
        "freebsd" | OperatingSystem.FREE_BSD
    }

    def "can create arbitrary operating system"() {
        def os = new DefaultOperatingSystem("arbitrary")

        expect:
        os.name == "arbitrary"
        os.toString() == "operating system 'arbitrary'"

        os.internalOs == OperatingSystem.UNIX
    }
}
