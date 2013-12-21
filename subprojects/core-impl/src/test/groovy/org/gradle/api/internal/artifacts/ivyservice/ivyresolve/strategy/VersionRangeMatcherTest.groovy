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

public class VersionRangeMatcherTest extends Specification {
    def matcher = new VersionRangeMatcher(new ExactVersionMatcher())

    def "handles selectors that use valid version range syntax"() {
        expect:
        matcher.canHandle("[1.0,2.0]")
        matcher.canHandle("[1.0,2.0[")
        matcher.canHandle("]1.0,2.0]")
        matcher.canHandle("]1.0,2.0[")
        matcher.canHandle("[1.0,)")
        matcher.canHandle("]1.0,)")
        matcher.canHandle("(,2.0]")
        matcher.canHandle("(,2.0[")
    }

    def "does not handle selectors that use no or invalid version range syntax"() {
        expect:
        !matcher.canHandle("1")
        !matcher.canHandle("1+")
        !matcher.canHandle("[1")
        !matcher.canHandle("[]")
        !matcher.canHandle("[1,2,3]")
    }

    def "all handled selectors are dynamic"() {
        expect:
        matcher.isDynamic("[1.0,2.0]")
        matcher.isDynamic("[1.0,)")
    }

    def "never needs metadata"() {
        expect:
        !matcher.needModuleMetadata("[1.0,2.0]", "1.0")
        !matcher.needModuleMetadata("[1.0,)", "1.0")
        !matcher.needModuleMetadata("1", "1")
    }

    def "accepts candidate versions that fall into the selector's range"() {
        expect:
        matcher.accept("[1.0,2.0]", "1.0")
        matcher.accept("[1.0,2.0]", "1.2.3")
        matcher.accept("[1.0,2.0]", "2.0")

        matcher.accept("[1.0,2.0[", "1.0")
        matcher.accept("[1.0,2.0[", "1.2.3")
        matcher.accept("[1.0,2.0[", "1.99")

        matcher.accept("]1.0,2.0]", "1.0.1")
        matcher.accept("]1.0,2.0]", "1.2.3")
        matcher.accept("]1.0,2.0]", "2.0")

        matcher.accept("]1.0,2.0[", "1.0.1")
        matcher.accept("]1.0,2.0[", "1.2.3")
        matcher.accept("]1.0,2.0[", "1.99")

        matcher.accept("[1.0,)", "1.0")
        matcher.accept("[1.0,)", "1.2.3")
        matcher.accept("[1.0,)", "2.3.4")

        matcher.accept("]1.0,)", "1.0.1")
        matcher.accept("]1.0,)", "1.2.3")
        matcher.accept("]1.0,)", "2.3.4")

        matcher.accept("(,2.0]", "0")
        matcher.accept("(,2.0]", "0.1.2")
        matcher.accept("(,2.0]", "2.0")

        matcher.accept("(,2.0[", "0")
        matcher.accept("(,2.0[", "0.1.2")
        matcher.accept("(,2.0[", "1.99")
    }

    def "accepts candidate versions that fall into the selector's range (adding qualifiers to the mix)"() {
        expect:
        matcher.accept("[1.0,2.0]", "1.5-dev-1")
        matcher.accept("[1.0,2.0]", "1.2.3-rc-2")
        matcher.accept("[1.0,2.0]", "2.0-final")

        matcher.accept("[1.0-dev-1,2.0[", "1.0")
        matcher.accept("[1.0,2.0-rc-2[", "2.0-rc-1")
        matcher.accept("[1.0,2.0[", "2.0-final")

        matcher.accept("]1.0-dev-1,2.0]", "1.0")
        matcher.accept("]1.0-rc-2,2.0]", "1.0-rc-3")

        matcher.accept("]1.0-dev-1,1.0-dev-3[", "1.0-dev-2")
        matcher.accept("]1.0-dev-1,1.0-rc-1[", "1.0-dev-99")
    }

    def "rejects candidate versions that don't fall into the selector's range"() {
        expect:
        !matcher.accept("[1.0,2.0]", "0.99")
        !matcher.accept("[1.0,2.0]", "2.0.1")
        !matcher.accept("[1.0,2.0]", "42")

        !matcher.accept("[1.0,2.0[", "0.99")
        !matcher.accept("[1.0,2.0[", "2.0")
        !matcher.accept("[1.0,2.0[", "42")

        !matcher.accept("]1.0,2.0]", "1.0")
        !matcher.accept("]1.0,2.0]", "2.0.1")
        !matcher.accept("]1.0,2.0]", "42")

        !matcher.accept("]1.0,2.0[", "1.0")
        !matcher.accept("]1.0,2.0[", "2.0")
        !matcher.accept("]1.0,2.0[", "42")

        !matcher.accept("[1.0,)", "0")
        !matcher.accept("[1.0,)", "0.99")

        !matcher.accept("]1.0,)", "0")
        !matcher.accept("]1.0,)", "1")
        !matcher.accept("]1.0,)", "1.0")

        !matcher.accept("(,2.0]", "2.0.1")
        !matcher.accept("(,2.0]", "42")

        !matcher.accept("(,2.0[", "2.0")
        !matcher.accept("(,2.0[", "42")
    }

    def "rejects candidate versions that don't fall into the selector's range (adding qualifiers to the mix)"() {
        expect:
        !matcher.accept("[1.0,2.0]", "2.5-dev-1")
        !matcher.accept("[1.0,2.0]", "1.0-rc-2")
        !matcher.accept("[1.0,2.0]", "1.0-final")

        !matcher.accept("[1.0-dev-2,2.0[", "1.0-dev-1")
        !matcher.accept("[1.0,2.0-rc-2[", "2.0-rc-2")
        !matcher.accept("[1.0,2.0-final[", "2.0")

        !matcher.accept("]1.0-dev-1,2.0]", "1.0-dev-1")
        !matcher.accept("]1.0-rc-2,2.0]", "1.0-dev-3")

        !matcher.accept("]1.0-dev-1,1.0-dev-3[", "1.0-dev-3")
        !matcher.accept("]1.0-dev-1,1.0-rc-1[", "1.0-final-0")
    }

    def "metadata-aware accept method delivers same results"() {
        def metadata = Stub(ModuleVersionMetaData) {
            getId() >> Stub(ModuleVersionIdentifier) {
                getVersion() >> metadataVersion
            }
        }

        expect:
        matcher.accept("[1.0,2.0]", metadata) == result

        where:
        metadataVersion | result
        "1.5"           | true
        "2.5"           | false
    }

    def "compares candidate versions against the selector's upper bound"() {
        expect:
        matcher.compare(range, "0.5") > 0
        matcher.compare(range, "1.0") > 0
        matcher.compare(range, "1.5") > 0
        matcher.compare(range, "2.0") < 0 // unsure why [1.0,2.0] isn't considered equal to 2.0 (apparently never returns 0)
        matcher.compare(range, "2.5") < 0

        where:
        range       | _
        "[1.0,2.0]" | _
        "[1.0,2.0[" | _
        "]1.0,2.0]" | _
        "]1.0,2.0[" | _
        "(,2.0]"    | _
        "(,2.0["    | _
    }

    def "selectors with infinite upper bound compare greater than any candidate version"() {
        expect:
        matcher.compare(range, "0.5") > 0
        matcher.compare(range, "1.0") > 0
        matcher.compare(range, "1.5") > 0
        matcher.compare(range, "2.0") > 0
        matcher.compare(range, "2.5") > 0

        where:
        range    | _
        "[1.0,)" | _
        "]1.0,)" | _
    }
}
