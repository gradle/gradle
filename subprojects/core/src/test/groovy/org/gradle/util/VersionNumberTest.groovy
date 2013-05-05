/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.util

import spock.lang.*

class VersionNumberTest extends Specification {
    def "parsing"() {
        expect:
        VersionNumber.parse("1") == new VersionNumber(1, 0, 0, null)
        VersionNumber.parse("1.0") == new VersionNumber(1, 0, 0, null)
        VersionNumber.parse("1.0.0") == new VersionNumber(1, 0, 0, null)

        VersionNumber.parse("1.2") == new VersionNumber(1, 2, 0, null)
        VersionNumber.parse("1.2.3") == new VersionNumber(1, 2, 3, null)

        VersionNumber.parse("1-rc1-SNAPSHOT") == new VersionNumber(1, 0, 0, "rc1-SNAPSHOT")
        VersionNumber.parse("1.2-rc1-SNAPSHOT") == new VersionNumber(1, 2, 0, "rc1-SNAPSHOT")
        VersionNumber.parse("1.2.3-rc1-SNAPSHOT") == new VersionNumber(1, 2, 3, "rc1-SNAPSHOT")

        VersionNumber.parse("1.rc1-SNAPSHOT") == new VersionNumber(1, 0, 0, "rc1-SNAPSHOT")
        VersionNumber.parse("1.2.rc1-SNAPSHOT") == new VersionNumber(1, 2, 0, "rc1-SNAPSHOT")
        VersionNumber.parse("1.2.3.rc1-SNAPSHOT") == new VersionNumber(1, 2, 3, "rc1-SNAPSHOT")

        VersionNumber.parse("11.22.33.44") == new VersionNumber(11, 22, 33, "44")
        VersionNumber.parse("11.44") == new VersionNumber(11, 44, 0, null)
        VersionNumber.parse("11.fortyfour") == new VersionNumber(11, 0, 0, "fortyfour")
    }

    def "unparseable version number is represented as UNKNOWN (0.0.0)"() {
        expect:
        VersionNumber.parse(null) == VersionNumber.UNKNOWN
        VersionNumber.parse("") == VersionNumber.UNKNOWN
        VersionNumber.parse("foo") == VersionNumber.UNKNOWN
        VersionNumber.parse("1.") == VersionNumber.UNKNOWN
        VersionNumber.parse("1.2.3-") == VersionNumber.UNKNOWN
    }

    def "accessors"() {
        when:
        def version = new VersionNumber(1, 2, 3, "foo")

        then:
        version.major == 1
        version.minor == 2
        version.micro == 3
        version.qualifier == "foo"
    }

    def "string representation"() {
        expect:
        new VersionNumber(1, 0, 0, null).toString() == "1.0.0"
        new VersionNumber(1, 2, 3, "foo").toString() == "1.2.3-foo"
    }

    def "equality"() {
        expect:
        new VersionNumber(1, 1, 1, null) == new VersionNumber(1, 1, 1, null)
        new VersionNumber(2, 1, 1, null) != new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 2, 1, null) != new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 1, 2, null) != new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 1, 1, "rc") != new VersionNumber(1, 1, 1, null)
    }

    def "comparison"() {
        expect:
        (new VersionNumber(1, 1, 1, null) <=> new VersionNumber(1, 1, 1, null)) == 0

        new VersionNumber(2, 1, 1, null) > new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 2, 1, null) > new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 1, 2, null) > new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 1, 1, "rc") < new VersionNumber(1, 1, 1, null)
        new VersionNumber(1, 1, 1, "beta") > new VersionNumber(1, 1, 1, "alpha")
        new VersionNumber(1, 1, 1, "RELEASE") > new VersionNumber(1, 1, 1, "beta")

        new VersionNumber(1, 1, 1, null) < new VersionNumber(2, 1, 1, null)
        new VersionNumber(1, 1, 1, null) < new VersionNumber(1, 2, 1, null)
        new VersionNumber(1, 1, 1, null) < new VersionNumber(1, 1, 2, null)
        new VersionNumber(1, 1, 1, null) > new VersionNumber(1, 1, 1, "rc")
        new VersionNumber(1, 1, 1, "alpha") < new VersionNumber(1, 1, 1, "beta")
        new VersionNumber(1, 1, 1, "beta") < new VersionNumber(1, 1, 1, "RELEASE")
    }

    def "base version"() {
        expect:
        new VersionNumber(1, 2, 3, null).baseVersion == new VersionNumber(1, 2, 3, null)
        new VersionNumber(1, 2, 3, "beta").baseVersion == new VersionNumber(1, 2, 3, null)
    }
}

