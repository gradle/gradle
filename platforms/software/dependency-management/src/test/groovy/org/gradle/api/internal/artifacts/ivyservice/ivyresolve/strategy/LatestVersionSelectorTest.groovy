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

class LatestVersionSelectorTest extends AbstractStringVersionSelectorTest {

    def "all handled selectors are dynamic"() {
        expect:
        isDynamic("latest.integration")
        isDynamic("latest.foo")
        isDynamic("latest.123")
    }

    def "always needs metadata"() {
        expect:
        requiresMetadata("latest.integration")
        requiresMetadata("latest.foo")
        requiresMetadata("latest.123")
    }

    def "accepts any fixed version"() {
        expect:
        accept("latest.integration", "1.0")
    }

    def "accepts a candidate version if its status is equal to or higher than the selector's status"() {
        def metadata = Stub(ComponentMetadata) {
            getStatus() >> "silver"
            getStatusScheme() >> ["bronze", "silver", "gold"]
        }

        expect:
        accept("latest.bronze", metadata)
        accept("latest.silver", metadata)
        !accept("latest.gold", metadata)
    }

    def "rejects a candidate version if selector's status is not contained in candidate's status scheme"() {
        def metadata = Stub(ComponentMetadata) {
            getStatus() >> "silver"
            getStatusScheme() >> ["bronze", "silver", "gold"]
        }

        expect:
        !accept("latest.other", metadata)
    }

    @Override
    VersionSelector getSelector(String selector) {
        return new LatestVersionSelector(selector)
    }
}
