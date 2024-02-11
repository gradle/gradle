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

class SubVersionSelectorTest extends AbstractStringVersionSelectorTest {
    def "all handled selectors are dynamic"() {
        expect:
        isDynamic("1+")
        isDynamic("1.2.3+")
    }

    def "never needs metadata"() {
        expect:
        !requiresMetadata("1+")
        !requiresMetadata("1.2.3+")
    }

    def "accepts candidate versions that literally match the selector up until the trailing '+'"() {
        expect:
        accept("1+", "11")
        accept("1.+", "1.2")
        accept("1.2.3+", "1.2.3.11")
        !accept("1+", "2")
        !accept("1.+", "11")
        !accept("1.2.3+", "1.2")
    }

    def "'+' is a valid selector which accepts everything"() {
        expect:
        accept("+", "11")
        accept("+", "1.2")
        accept("+", "1.2.3.11")
        accept("+", "2")
        accept("+", "11")
        accept("+", "1.2")
    }

    def "metadata-aware accept method delivers same results"() {
        def metadata = Stub(ComponentMetadata) {
            getId() >> Stub(ModuleVersionIdentifier) {
                getVersion() >> metadataVersion
            }
        }

        expect:
        accept("1.+", metadata) == result

        where:
        metadataVersion | result
        "1.5"           | true
        "2.5"           | false
    }

    @Override
    VersionSelector getSelector(String selector) {
        return new SubVersionSelector(selector)
    }
}
