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

import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ModuleVersionIdentifier

class VersionRangeSelectorTest extends AbstractStringVersionSelectorTest {

    def "all handled selectors are dynamic"() {
        expect:
        isDynamic("[1.0,2.0]")
        isDynamic("[1.0,)")
        isDynamic("[1.0]")
    }

    def "never needs metadata"() {
        expect:
        !requiresMetadata("[1.0,2.0]")
        !requiresMetadata("[1.0,)")
        !requiresMetadata("[1.0]")
    }

    def "excluded upper bound corner cases"() {
        expect:
        !accept("[1.0,2.0)", "2.0-final")
        !accept("[1.0,2.0)", "2.0-dev")
        !accept("[1.0,2.0)", "2.0.0-dev")
    }

    def "accepts candidate versions that fall into the selector's range"() {
        expect:
        accept("[1.0,2.0]", "1.0")
        accept("[1.0,2.0]", "1.2.3")
        accept("[1.0,2.0]", "2.0")

        accept("[1.0,2.0[", "1.0")
        accept("[1.0,2.0[", "1.2.3")
        accept("[1.0,2.0[", "1.99")

        accept("]1.0,2.0]", "1.0.1")
        accept("]1.0,2.0]", "1.2.3")
        accept("]1.0,2.0]", "2.0")

        accept("]1.0,2.0[", "1.0.1")
        accept("]1.0,2.0[", "1.2.3")
        accept("]1.0,2.0[", "1.99")

        accept("[1.0,)", "1.0")
        accept("[1.0,)", "1.2.3")
        accept("[1.0,)", "2.3.4")

        accept("]1.0,)", "1.0.1")
        accept("]1.0,)", "1.2.3")
        accept("]1.0,)", "2.3.4")

        accept("(,2.0]", "0")
        accept("(,2.0]", "0.1.2")
        accept("(,2.0]", "2.0")

        accept("(,2.0[", "0")
        accept("(,2.0[", "0.1.2")
        accept("(,2.0[", "1.99")
    }

    def "accepts candidate versions that fall into the selector's range (adding qualifiers to the mix)"() {
        expect:
        accept("[1.0,2.0]", "1.5-dev-1")
        accept("[1.0,2.0]", "1.2.3-rc-2")
        accept("[1.0,2.0]", "2.0-final")

        accept("[1.0-dev-1,2.0[", "1.0")
        accept("[1.0,2.0-rc-2[", "2.0-rc-1")

        accept("]1.0-dev-1,2.0]", "1.0")
        accept("]1.0-rc-2,2.0]", "1.0-rc-3")

        accept("]1.0-dev-1,1.0-dev-3[", "1.0-dev-2")
        accept("]1.0-dev-1,1.0-rc-1[", "1.0-dev-99")
    }

    def "rejects candidate versions that don't fall into the selector's range"() {
        expect:
        !accept("[1.0,2.0]", "0.99")
        !accept("[1.0,2.0]", "2.0.1")
        !accept("[1.0,2.0]", "42")

        !accept("[1.0,2.0[", "0.99")
        !accept("[1.0,2.0[", "2.0")
        !accept("[1.0,2.0[", "42")
        !accept("[1.0,2.0[", "2.0-final")

        !accept("]1.0,2.0]", "1.0")
        !accept("]1.0,2.0]", "2.0.1")
        !accept("]1.0,2.0]", "42")

        !accept("]1.0,2.0[", "1.0")
        !accept("]1.0,2.0[", "2.0")
        !accept("]1.0,2.0[", "42")

        !accept("[1.0,)", "0")
        !accept("[1.0,)", "0.99")

        !accept("]1.0,)", "0")
        !accept("]1.0,)", "1")
        !accept("]1.0,)", "1.0")

        !accept("(,2.0]", "2.0.1")
        !accept("(,2.0]", "42")

        !accept("(,2.0[", "2.0")
        !accept("(,2.0[", "42")
    }

    def "rejects candidate versions that don't fall into the selector's range (adding qualifiers to the mix)"() {
        expect:
        !accept("[1.0,2.0]", "2.5-dev-1")
        !accept("[1.0,2.0]", "1.0-rc-2")
        !accept("[1.0,2.0]", "1.0-final")

        !accept("[1.0-dev-2,2.0[", "1.0-dev-1")
        !accept("[1.0,2.0-rc-2[", "2.0-rc-2")
        !accept("[1.0,2.0-final[", "2.0")

        !accept("]1.0-dev-1,2.0]", "1.0-dev-1")
        !accept("]1.0-rc-2,2.0]", "1.0-dev-3")

        !accept("]1.0-dev-1,1.0-dev-3[", "1.0-dev-3")
        !accept("]1.0-dev-1,1.0-rc-1[", "1.0-final-0")
    }

    def "metadata-aware accept method delivers same results"() {
        def metadata = Stub(ComponentMetadata) {
            getId() >> Stub(ModuleVersionIdentifier) {
                getVersion() >> metadataVersion
            }
        }

        expect:
        accept("[1.0,2.0]", metadata) == result

        where:
        metadataVersion | result
        "1.5"           | true
        "2.5"           | false
    }

    def "single-value range accepts only exact match"() {
        expect:
        accept("[1.0]", "1.0")

        !accept("[1.0]", "1.1")
        !accept("[1.0]", "1")
        !accept("[1.0]", "1.01")
        !accept("[1.0]", "0.9")
        !accept("[1.0]", "1.0-beta1")
    }

    @Override
    VersionSelector getSelector(String selector) {
        return new VersionRangeSelector(selector, new DefaultVersionComparator().asVersionComparator(), new VersionParser())
    }
}
