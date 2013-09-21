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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData

import spock.lang.Specification

class ExactVersionMatcherTest extends Specification {
    def matcher = new ExactVersionMatcher()

    def "can handle any version selector"() {
        expect:
        matcher.canHandle("1.0")
        matcher.canHandle("[1.0,2.0]")
        matcher.canHandle("!@#%")
    }

    def "considers selector as static"() {
        expect:
        !matcher.isDynamic("1.0")
        !matcher.isDynamic("[1.0,2.0]")
    }

    def "doesn't need metadata"() {
        expect:
        !matcher.needModuleMetadata("1.0", "1.0")
        !matcher.needModuleMetadata("[1.0,2.0]", "2.0")
    }

    def "accepts candidate version iff it literally matches the selector"() {
        expect:
        matcher.accept("", "")
        matcher.accept("1.0", "1.0")
        matcher.accept("2.0", "2.0")
        matcher.accept("!@#%", "!@#%")
        matcher.accept("hey joe", "hey joe")
        !matcher.accept("1.0", "1.1")
        !matcher.accept("2.0", "3.0")
        !matcher.accept("!@#%", "%#@!")
        !matcher.accept("hey joe", "hoe hey")
    }

    def "does not accept candidate version that differs in separator"() {
        expect:
        !matcher.accept("1.0", "1_0")
        !matcher.accept("1_0", "1-0")
        !matcher.accept("1-0", "1+0")
        !matcher.accept("1.a.2", "1a2")
    }

    def "does not accept candidate version that has different number of trailing .0's"() {
        expect:
        !matcher.accept("1.0.0", "1.0")
        !matcher.accept("1", "1.0.0")
    }

    def "does not accept candidate version that has different capitalization"() {
        !matcher.accept("1.0-alpha", "1.0-ALPHA")
    }

    def "supports metadata-aware accept method (with same result)"() {
        def metadata = Stub(ModuleVersionMetaData) {
            getId() >> Stub(ModuleVersionIdentifier) {
                getVersion() >> metadataVersion
            }
        }

        expect:
        matcher.accept("1.0", metadata) == result

        where:
        metadataVersion | result
        "1.0"           | true
        "2.0"           | false
    }

    def "compares versions lexicographically"() {
        expect:
        matcher.compare(v1, v2) < 0
        matcher.compare(v2, v1) > 0
        matcher.compare(v1, v1) == 0
        matcher.compare(v2, v2) == 0

        where:
        v1          | v2
        "1.0"       | "2.0"
        "1.0"       | "1.1"
        "1.0.1"     | "1.1.0"
        "1.2"       | "1.2.3"
        "1.0-1"     | "1.0-2"
        "1.0.a"     | "1.0.b"
        "1.0-alpha" | "1.0"
        "1.0-alpha" | "1.0-beta"
        "1.0.alpha" | "1.0.b"
    }

    def "gives some special treatment to 'dev', 'rc', and 'final' qualifiers"() {
        expect:
        matcher.compare(v1, v2) < 0
        matcher.compare(v2, v1) > 0
        matcher.compare(v1, v1) == 0
        matcher.compare(v2, v2) == 0

        where:
        v1          | v2
        "1.0-dev-1" | "1.0"
        "1.0-dev-1" | "1.0-dev-2"
        "1.0-rc-1"  | "1.0"
        "1.0-rc-1"  | "1.0-rc-2"
        "1.0-final" | "1.0"
        "1.0-dev-1" | "1.0-rc-1"
        "1.0-rc-1"  | "1.0-final"
        "1.0-dev-1" | "1.0-final"
    }

    def "compares identical versions equal"() {
        expect:
        matcher.compare(v1, v2) == 0
        matcher.compare(v2, v1) == 0

        where:
        v1        | v2
        ""        | ""
        "1"       | "1"
        "1.0.0"   | "1.0.0"
        "!@#%"    | "!@#%"
        "hey joe" | "hey joe"
    }

    def "compares versions that differ only in separators equal"() {
        expect:
        matcher.compare("1.0", "1_0") == 0
        matcher.compare("1_0", "1-0") == 0
        matcher.compare("1-0", "1+0") == 0
        matcher.compare("1.a.2", "1a2") == 0 // number-word and word-number boundaries are considered separators
    }

    def "compares unrelated versions unequal"() {
        expect:
        matcher.compare("1.0", "") != 0
        matcher.compare("1.0", "!@#%") != 0
        matcher.compare("1.0", "hey joe") != 0
    }

    // original Ivy behavior - should we change it?
    def "does not compare versions with different number of trailing .0's equal"() {
        expect:
        matcher.compare(v1, v2) > 0
        matcher.compare(v2, v1) < 0

        where:
        v1          | v2
        "1.0.0"     | "1.0"
        "1.0.0"     | "1"
    }

    def "does not compare versions with different capitalization equal"() {
        expect:
        matcher.compare(v1, v2) > 0
        matcher.compare(v2, v1) < 0

        where:
        v1          | v2
        "1.0-alpha" | "1.0-ALPHA"
    }
}
