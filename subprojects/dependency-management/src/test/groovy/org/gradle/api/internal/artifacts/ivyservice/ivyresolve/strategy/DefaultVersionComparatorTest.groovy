/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.VersionInfo
import spock.lang.Specification

class DefaultVersionComparatorTest extends Specification {

    def versionParser = new VersionParser()
    def comparator = new DefaultVersionComparator()

    def compare(String s1, String s2) {
        return comparator.compare(new VersionInfo(versionParser.transform(s1)), new VersionInfo(versionParser.transform(s2)))
    }

    def "numeric parts are compared as numbers"() {
        expect:
        compare(smaller, larger) < 0
        compare(larger, smaller) > 0
        compare(smaller, smaller) == 0
        compare(larger, larger) == 0

        where:
        smaller  | larger
        "1.0"    | "2.0"
        "1.0"    | "1.1"
        "1.2"    | "1.10"
        "1.0.1"  | "1.1.0"
        "1.2"    | "1.2.3"
        "12"     | "12.2.3"
        "12"     | "13"
        "1.0-1"  | "1.0-2"
        "1.0-1"  | "1.0.2"
        "1.0-1"  | "1+0_2"
        "2.11.4" | "2.11.4+4"
    }

    def "non-numeric parts are compared alphabetically and are case sensitive"() {
        expect:
        compare(smaller, larger) < 0
        compare(larger, smaller) > 0
        compare(smaller, smaller) == 0
        compare(larger, larger) == 0

        where:
        smaller     | larger
        "1.0.a"     | "1.0.b"
        "1.0.A"     | "1.0.b"
        "1.0-alpha" | "1.0-beta"
        "1.0-ALPHA" | "1.0-BETA"
        "1.0-ALPHA" | "1.0-alpha"
        "1.0.alpha" | "1.0.b"
        "alpha"     | "beta"
        "1.0-a"     | "1.0-alpha"
        "1.0-a"     | "1.0-a1"
    }

    def "numeric parts are considered larger than non-numeric ones"() {
        expect:
        compare(smaller, larger) < 0
        compare(larger, smaller) > 0
        compare(smaller, smaller) == 0
        compare(larger, larger) == 0

        where:
        smaller             | larger
        "1.0-alpha"         | "1.0.1"
        "a.b.c"             | "a.b.123"
        "a"                 | "123"
        "1.0.0-alpha.beta"  | "1.0.0-alpha.1"
    }

    def "extra trailing parts that contain no digits make the version smaller"() {
        expect:
        compare(smaller, larger) < 0
        compare(larger, smaller) > 0
        compare(smaller, smaller) == 0
        compare(larger, larger) == 0

        where:
        smaller     | larger
        "1.0-alpha" | "1.0"
        "1.0.a"     | "1.0"
        "1.beta.a"  | "1.beta"
        "a-b-c"     | "a.b"
    }

    def "identical versions are equal"() {
        expect:
        compare(v1, v2) == 0
        compare(v2, v1) == 0

        where:
        v1        | v2
        ""        | ""
        "1"       | "1"
        "1.0.0"   | "1.0.0"
        "!@#%"    | "!@#%"
        "hey joe" | "hey joe"
    }

    def "versions that differ only in separators are equal"() {
        expect:
        compare("1.0", "1_0") == 0
        compare("1_0", "1-0") == 0
        compare("1-0", "1+0") == 0
        compare("1.a.2", "1a2") == 0 // number-word and word-number boundaries are considered separators
    }

    def "leading zeros in numeric parts are ignored"() {
        expect:
        compare("01.0", "1.0") == 0
        compare("1.0", "01.0") == 0
        compare("001.2.003", "0001.02.3") == 0
    }

    // original Ivy behavior - should we change it?
    def "versions with extra trailing zero parts are considered larger"() {
        expect:
        compare(larger, smaller) > 0
        compare(smaller, larger) < 0

        where:
        larger  | smaller
        "1.0.0" | "1.0"
        "1.0.0" | "1"
    }

    def "incorrectly compares Maven snapshot-like versions (current behaviour not necessarily desired behaviour"() {
        expect:
        compare(smaller, larger) < 0
        compare(larger, smaller) > 0
        compare(smaller, smaller) == 0
        compare(larger, larger) == 0

        where:
        smaller                   | larger
        "1.0-SNAPSHOT"            | "1.0"
        "1.0"                     | "1.0-20150201.121010-123" // incorrect!
        "1.0-20150201.121010-123" | "1.0-20150201.121010-124"
        "1.0-20150201.121010-123" | "1.0-20150201.131010-1"
        "1.0-SNAPSHOT"            | "1.0-20150201.131010-1" // probably not right
        "1.0"                     | "1.1-SNAPSHOT"
        "1.0"                     | "1.1-20150201.121010-12"
        "1.1-20150201.121010-12"  | "1.2"
    }

    def "can compare Version objects"() {
        def v1 = Stub(Version) {
            getParts() >> ["1", "2"]
            getNumericParts() >> [1, 2]
        }
        def v2 = Stub(Version) {
            getParts() >> ["1", "3"]
            getNumericParts() >> [1, 3]
        }

        expect:
        def versionComparator = comparator.asVersionComparator()
        versionComparator.compare(v1, v2) < 0
    }

    def "special qualifiers are treated differently"() {
        expect:
        compare(smaller, larger) < 0
        compare(larger, smaller) > 0
        compare(smaller, smaller) == 0
        compare(larger, larger) == 0

        where:
        smaller        | larger
        "1.0-alpha"    | "1.0-rc"
        "1.0-alpha-1"  | "1.0-rc-1"
        "1.0-alpha"    | "1.0-snapshot"
        "1.0-alpha"    | "1.0-final"
        "1.0-alpha"    | "1.0-ga"
        "1.0-alpha"    | "1.0-release"
        "1.0-alpha"    | "1.0-sp"
        "1.0-alpha"    | "1.0"

        "1.0-rc-1"     | "1.0-rc-2"
        "1.0-rc"       | "1.0-snapshot"
        "1.0-rc"       | "1.0-final"
        "1.0-rc-1"     | "1.0-final"
        "1.0-rc"       | "1.0-ga"
        "1.0-rc"       | "1.0-release"
        "1.0-rc-1"     | "1.0-release"
        "1.0-rc"       | "1.0-sp"
        "1.0-rc"       | "1.0"
        "1.0-rc-1"     | "1.0"

        "1.0-snapshot" | "1.0-final"
        "1.0-snapshot" | "1.0-ga"
        "1.0-snapshot" | "1.0-release"
        "1.0-snapshot" | "1.0-sp"
        "1.0-snapshot" | "1.0"

        "1.0-final"    | "1.0-ga"
        "1.0-final"    | "1.0-release"
        "1.0-final"    | "1.0-sp"
        "1.0-final"    | "1.0"

        "1.0-ga"       | "1.0-release"
        "1.0-ga"       | "1.0-sp"
        "1.0-ga"       | "1.0-"

        "1.0-release"  | "1.0-sp"
        "1.0-release"  | "1.0"

        "1.0-sp"       | "1.0"
    }

    def "special qualifiers do not depend on the separator used"() {
        expect:
        compare(smaller, larger) < 0
        compare(larger, smaller) > 0
        compare(smaller, smaller) == 0
        compare(larger, larger) == 0

        where:
        smaller        | larger
        "1.0.alpha"    | "1.0.rc"
        "1.0.rc"       | "1.0.snapshot"
        "1.0.snapshot" | "1.0.final"
        "1.0.final"    | "1.0.ga"
        "1.0.ga"       | "1.0.release"
        "1.0.release"  | "1.0.sp"
        "1.0.sp"       | "1.0"
    }

    def 'dev considered lower than any other string qualifier'() {
        expect:
        compare(smaller, larger) < 0
        compare(larger, smaller) > 0
        compare(smaller, smaller) == 0
        compare(larger, larger) == 0

        where:
        smaller     | larger
        "1.0-dev"   | "1.0.1"
        "1.0-dev"   | "1.0-alpha"
        "1.0-dev"   | "1.0-XXX"
        "1.0-dev"   | "1.0-rc"
        "1.0-dev"   | "1.0-snapshot"
        "1.0-dev"   | "1.0-final"
        "1.0-dev"   | "1.0-ga"
        "1.0-dev"   | "1.0-release"
        "1.0-dev"   | "1.0-sp"
        "1.0-dev"   | "1.0"

        "1.0-dev-1" | "1.0-dev-2"
        "1.0-dev-1" | "1.0-xx-1"
        "1.0-dev-1" | "1.0-rc-1"
        "1.0-dev-1" | "1.0-final"
    }

    def 'case for special qualifiers is ignored'() {
        expect:
        compare("1.0-rc", "1.0.RC") == 0
        compare("1.0-rc-1", "1.0.RC.1") == 0
        compare("1.0-snapshot", "1.0.SNAPSHOT") == 0
        compare("1.0-final", "1.0.FINAL") == 0
        compare("1.0-ga", "1.0.GA") == 0
        compare("1.0-release", "1.0.RELEASE") == 0
        compare("1.0-sp", "1.0.SP") == 0
    }
}
