/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.swift

import org.gradle.util.VersionNumber
import spock.lang.Specification
import spock.lang.Subject

import static SwiftLanguageVersion.*

@Subject(SwiftLanguageVersion)
class SwiftLanguageVersionTest extends Specification {
    def "can associate the right Swift language version"() {
        expect:
        SwiftLanguageVersion.of(VersionNumber.parse(compilerVersion)) == languageVersion

        where:
        // See https://swift.org/download
        compilerVersion | languageVersion
        '4.0.3'         | SWIFT4
        '4.0.2'         | SWIFT4
        '4.0'           | SWIFT4
        '3.1.1'         | SWIFT3
        '3.1'           | SWIFT3
        '3.0.2'         | SWIFT3
        '3.0.1'         | SWIFT3
        '3.0'           | SWIFT3
        '2.2.1'         | SWIFT2
        '2.2'           | SWIFT2
    }

    def "throws exception when Swift language is unknown for specified compiler version"() {
        when:
        SwiftLanguageVersion.of(VersionNumber.parse("99.0.1"))

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Swift language version is unknown for the specified swift compiler version (99.0.1)'
    }
}
