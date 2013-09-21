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

class ChainVersionMatcherTest extends Specification {
    def chain = new ChainVersionMatcher()
    def matcher1 = Mock(VersionMatcher)
    def matcher2 = Mock(VersionMatcher)
    def matcher3 = Mock(VersionMatcher)

    def setup() {
        chain.add(matcher1)
        chain.add(matcher2)
        chain.add(matcher3)
    }

    def "doesn't support canHandle method (no known use case)"() {
        when:
        chain.canHandle("1")

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains("canHandle")
    }

    def "delegates isDynamic to first matcher that can handle the selector"() {
        when:
        def result = chain.isDynamic("1+")

        then:
        1 * matcher1.canHandle("1+") >> false
        1 * matcher2.canHandle("1+") >> true
        1 * matcher2.isDynamic("1+") >> true
        0 * _

        and:
        result
    }

    def "delegates needModuleMetadata to first matcher that can handle the selector"() {
        when:
        def result = chain.needModuleMetadata("1+", "2")

        then:
        1 * matcher1.canHandle("1+") >> false
        1 * matcher2.canHandle("1+") >> true
        1 * matcher2.needModuleMetadata("1+", "2") >> false
        0 * _

        and:
        !result
    }

    def "delegates accept to first matcher that can handle the selector"() {
        when:
        def result = chain.accept("1+", "2")

        then:
        1 * matcher1.canHandle("1+") >> false
        1 * matcher2.canHandle("1+") >> true
        1 * matcher2.accept("1+", "2") >> false
        0 * _

        and:
        !result
    }

    def "delegates metadata-aware accept to first matcher that can handle the selector"() {
        def metadata = Stub(ModuleVersionMetaData) {
            getId() >> Stub(ModuleVersionIdentifier) {
                getVersion() >> "2"
            }
        }

        when:
        def result = chain.accept("1+", metadata)

        then:
        1 * matcher1.canHandle("1+") >> false
        1 * matcher2.canHandle("1+") >> true
        1 * matcher2.accept("1+", metadata) >> false
        0 * _

        and:
        !result
    }

    def "delegates compare to first matcher that can handle the selector"() {
        def comparator = Stub(Comparator)

        when:
        def result = chain.compare("1+", "2")

        then:
        1 * matcher1.canHandle("1+") >> false
        1 * matcher2.canHandle("1+") >> true
        1 * matcher2.compare("1+", "2") >> -3
        0 * _

        and:
        result == -3
    }

    def "complains if a version selector isn't matched by any matcher"() {
        when:
        chain.accept("1+", "1")

        then:
        IllegalArgumentException e = thrown()
        e.message == "Invalid version selector: 1+"
    }
}
