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
package org.gradle.nativebinaries.platform.internal

import org.gradle.api.InvalidUserDataException
import spock.lang.Specification

class OperatingSystemNotationParserTest extends Specification {
    def parser = OperatingSystemNotationParser.parser()

    def "fails when parsing unknown operating system"() {
        when:
        parser.parseNotation("bad")

        then:
        def e = thrown(InvalidUserDataException)
        e.message.contains("One of the following values: 'windows', 'osx', 'mac os x', 'linux', 'solaris', 'sunos'")
    }

    def "parses windows"() {
        when:
        def os = parser.parseNotation("windows")
        then:
        os.windows
        !os.macOsX
        !os.linux
        !os.solaris
        !os.freeBSD
    }

    def "parses osx"() {
        when:
        def os = parser.parseNotation(osString)

        then:
        !os.windows
        os.macOsX
        !os.linux
        !os.solaris
        !os.freeBSD

        where:
        osString << ["osx", "mac os x"]
    }

    def "parses linux"() {
        when:
        def os = parser.parseNotation("linux")

        then:
        !os.windows
        !os.macOsX
        os.linux
        !os.solaris
        !os.freeBSD
    }

    def "parses solaris"() {
        when:
        def os = parser.parseNotation(osString)

        then:
        !os.windows
        !os.macOsX
        !os.linux
        os.solaris
        !os.freeBSD

        where:
        osString << ["solaris", "sunos"]
    }

    def "parses FreeBSD"() {
        when:
        def os = parser.parseNotation(osString)

        then:
        !os.windows
        !os.macOsX
        !os.linux
        !os.solaris
        os.freeBSD

        where:
        osString << ["FreeBSD", "freebsd"]
    }
}
