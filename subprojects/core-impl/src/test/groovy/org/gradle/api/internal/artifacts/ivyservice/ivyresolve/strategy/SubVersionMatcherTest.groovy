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

class SubVersionMatcherTest extends Specification {
    def matcher = new SubVersionMatcher(new ExactVersionMatcher())

    def "handles selectors that end in '+'"() {
        expect:
        matcher.canHandle("1+")
        matcher.canHandle("1.2.3+")
        !matcher.canHandle("1")
        !matcher.canHandle("1.+.3")
    }

    def "all handled selectors are dynamic"() {
        expect:
        matcher.isDynamic("1+")
        matcher.isDynamic("1.2.3+")
    }

    def "never needs metadata"() {
        expect:
        !matcher.needModuleMetadata("1+", "1")
        !matcher.needModuleMetadata("1.2.3+", "1.2.3")
    }

    def "accepts candidate versions that literally match the selector up until the trailing '+'"() {
        expect:
        matcher.accept("1+", "11")
        matcher.accept("1.+", "1.2")
        matcher.accept("1.2.3+", "1.2.3.11")
        !matcher.accept("1+", "2")
        !matcher.accept("1.+", "11")
        !matcher.accept("1.2.3+", "1.2")
    }

    def "metadata-aware accept method delivers same results"() {
        def metadata = Stub(ModuleVersionMetaData) {
            getId() >> Stub(ModuleVersionIdentifier) {
                getVersion() >> metadataVersion
            }
        }

        expect:
        matcher.accept("1.+", metadata) == result

        where:
        metadataVersion | result
        "1.5"           | true
        "2.5"           | false
    }

    def "considers a '+' selector greater than any matching candidate version"() {
        expect:
        matcher.compare("1+", "11") > 0
        matcher.compare("1.+", "1.2") > 0
        matcher.compare("1.2.3+", "1.2.3.11") > 0
    }

    def "falls back to the provided comparator if selector doesn't match candidate version"() {
        expect:
        matcher.compare("1+", "2") < 0
        matcher.compare("1+", "0.5") > 0
    }
}
