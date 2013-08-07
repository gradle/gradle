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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionMetaData

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
        matcher.accept("1.0", "1.0")
        matcher.accept("2.0", "2.0")
        matcher.accept("!@#%", "!@#%")
        !matcher.accept("1.0", "1.1")
        !matcher.accept("2.0", "3.0")
        !matcher.accept("!@#%", "%#@!")
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
        matcher.compare("1.0", "2.0") < 0
        matcher.compare("1.0", "1.1") < 0
        matcher.compare("1.0.1", "1.1.0") < 0

        matcher.compare("1", "1") == 0
        matcher.compare("1.0", "1.0") == 0

        matcher.compare("2.0", "1.0") > 0
        matcher.compare("1.1", "1.0") > 0
        matcher.compare("1.1.0", "1.0.1") > 0

        matcher.compare("1.2.3", "1.2") > 0
        matcher.compare("1.2", "1.2.3") < 0
    }

    def "gives special treatment to 'dev', 'rc', and 'final' qualifiers"() {
        expect:
        matcher.compare("1.0-dev-1", "1.0") < 0
        matcher.compare("1.0", "1.0-dev-1") > 0
        matcher.compare("1.0-dev-1", "1.0-dev-2") < 0
        matcher.compare("1.0-dev-2", "1.0-dev-1") > 0

        matcher.compare("1.0-rc-1", "1.0") < 0
        matcher.compare("1.0", "1.0-rc-1") > 0
        matcher.compare("1.0-rc-1", "1.0-rc-2") < 0
        matcher.compare("1.0-rc-2", "1.0-rc-1") > 0

        matcher.compare("1.0-final", "1.0") < 0
        matcher.compare("1.0", "1.0-final") > 0

        matcher.compare("1.0-dev-1", "1.0-rc-1") < 0
        matcher.compare("1.0-dev-2", "1.0-rc-1") < 0

        matcher.compare("1.0-rc-1", "1.0-final") < 0
        matcher.compare("1.0-rc-2", "1.0-final") < 0

        matcher.compare("1.0-final", "1.0-dev-1") > 0
        matcher.compare("1.0-final", "1.0-dev-2") > 0
    }

    def "versions that differ only in separators compare equal"() {
        expect:
        matcher.compare("1.0", "1_0") == 0
        matcher.compare("1_0", "1-0") == 0
        matcher.compare("1-0", "1+0") == 0
        matcher.compare("1.a.2", "1a2") == 0 // number-word and word-number boundaries are considered separators
    }
}
