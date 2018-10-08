/*
 * Copyright 2014 the original author or authors.
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

import spock.lang.Specification
import spock.lang.Unroll

class DefaultVersionSelectorSchemeTest extends Specification {
    def matcher = new DefaultVersionSelectorScheme(new DefaultVersionComparator(), new VersionParser())

    def "creates version range selector"() {
        expect:
        matcher.parseSelector(selector) instanceof VersionRangeSelector

        where:
        selector << [
            "[1.0,2.0]",
            "[1.0,2.0)",
            "]1.0,2.0]",
            "]1.0,2.0)",
            "[1.0,)",
            "]1.0,)",
            "(,2.0]",
            "(,2.0)",
            "[3]",
            "[1.0]",
        ]
    }

    def "creates sub version selector"() {
        expect:
        matcher.parseSelector(selector) instanceof SubVersionSelector

        where:
        selector << [
            "1+",
            "1.2.3+"
        ]
    }

    def "creates latest version selector"() {
        expect:
        matcher.parseSelector(selector) instanceof LatestVersionSelector

        where:
        selector << [
            "latest.integration",
            "latest.foo",
            "latest.123"
        ]
    }

    def "creates exact version selector as default"() {
        expect:
        matcher.parseSelector(selector) instanceof ExactVersionSelector

        where:
        selector << [
            "1.0",
            "!@#%",
            "1",
            "1.+.3",
            "[1",
            "[]",
            "[1,2,3]",
        ]
    }

    @Unroll
    def "computes rejection selector for strict dependency version"() {
        given:
        def normal = matcher.parseSelector(selector)

        when:
        def reject = matcher.complementForRejection(normal)

        then:
        reject instanceof InverseVersionSelector
        reject.selector == complement

        where:
        selector         | complement
        '20'             | '!(20)'
        '[3,10]'         | '!([3,10])'
        '(,10)'          | '!((,10))'
    }

    @Unroll
    def "cannot compute rejection selector for strict dependency versions"() {
        given:
        def normal = matcher.parseSelector(selector)

        when:
        matcher.complementForRejection(normal)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == error

        where:
        selector         | error
        'latest.release' | 'Version \'latest.release\' cannot be converted to a strict version constraint.'
        '1+'             | 'Version \'1+\' cannot be converted to a strict version constraint.'
        '[3,)'           | 'Version \'[3,)\' cannot be converted to a strict version constraint.'
    }
}
