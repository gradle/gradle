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

import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData
import spock.lang.Specification

class LatestVersionMatcherTest extends Specification {
    def matcher = new LatestVersionMatcher()

    def "handles selectors starting with 'latest.'"() {
        expect:
        matcher.canHandle("latest.integration")
        matcher.canHandle("latest.foo")
        matcher.canHandle("latest.123")
        !matcher.canHandle("1.0")
        !matcher.canHandle("[1.0,2.0]")
    }

    def "all handled selectors are dynamic"() {
        expect:
        matcher.isDynamic("latest.integration")
        matcher.isDynamic("latest.foo")
        matcher.isDynamic("latest.123")
    }

    def "always needs metadata"() {
        expect:
        matcher.needModuleMetadata("latest.integration", "1.0")
        matcher.needModuleMetadata("latest.foo", "1.0")
        matcher.needModuleMetadata("latest.123", "1.0")
    }

    def "only supports metadata-aware accept method"() {
        when:
        matcher.accept("latest.integration", "1.0")

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains("accept")
    }

    def "accepts a candidate version iff its status is equal to or higher than the selector's status"() {
        def metadata = Stub(ModuleVersionMetaData) {
            getStatus() >> "silver"
            getStatusScheme() >> ["bronze", "silver", "gold"]
        }

        expect:
        matcher.accept("latest.bronze", metadata)
        matcher.accept("latest.silver", metadata)
        !matcher.accept("latest.gold", metadata)
    }

    def "rejects a candidate version if selector's status is not contained in candidate's status scheme"() {
        def metadata = Stub(ModuleVersionMetaData) {
            getStatus() >> "silver"
            getStatusScheme() >> ["bronze", "silver", "gold"]
        }

        expect:
        !matcher.accept("latest.other", metadata)
    }

    def "cannot tell which of version selector and candidate version is greater"() {
        expect:
        matcher.compare("latest.integration", "1.0") == 0
        matcher.compare("latest.release", "2.0") == 0
    }
}
