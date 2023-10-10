/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.local

import org.gradle.internal.hash.Hashing
import spock.lang.Specification

class CompositeLocallyAvailableResourceFinderTest extends Specification {

    def "interrogates composites in turn as needed"() {
        given:
        def f1 = Mock(LocallyAvailableResourceFinder)
        def c1 = Mock(LocallyAvailableResourceCandidates)
        def f2 = Mock(LocallyAvailableResourceFinder)
        def c2 = Mock(LocallyAvailableResourceCandidates)
        def f3 = Mock(LocallyAvailableResourceFinder)
        def c3 = Mock(LocallyAvailableResourceCandidates)
        def hash = Hashing.sha1().hashString("abc")

        def composite = new CompositeLocallyAvailableResourceFinder<String>([f1, f2, f3])
        def criterion = "abc"

        when:
        def candidates = composite.findCandidates(criterion)

        then:
        1 * f1.findCandidates(criterion) >> c1

        then:
        1 * f2.findCandidates(criterion) >> c2

        then:
        1 * f3.findCandidates(criterion) >> c3

        and:
        0 * c1._(*_)
        0 * c2._(*_)
        0 * c3._(*_)

        when:
        def isNone = candidates.isNone()

        then:
        1 * c1.isNone() >> true

        and:
        1 * c2.isNone() >> false
        0 * c3.isNone()

        when:
        def match = candidates.findByHashValue(hash)

        then:
        1 * c1.findByHashValue(hash) >> null

        and:
        1 * c2.findByHashValue(hash) >> Mock(LocallyAvailableResource)
        0 * c3.findByHashValue(_)
    }
}
