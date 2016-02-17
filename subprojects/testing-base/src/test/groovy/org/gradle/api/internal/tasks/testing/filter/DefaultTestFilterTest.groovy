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



package org.gradle.api.internal.tasks.testing.filter

import org.gradle.api.InvalidUserDataException
import spock.lang.Specification

class DefaultTestFilterTest extends Specification {

    def spec = new DefaultTestFilter()

    def "allows configuring test names"() {
        expect:
        spec.includePatterns.isEmpty()
        spec.excludePatterns.isEmpty()

        when:
        spec.includeTestsMatching("*fooMethod")
        spec.includeTestsMatching("*.FooTest.*")

        then: spec.includePatterns == ["*fooMethod", "*.FooTest.*"] as Set

        when: spec.setIncludePatterns("x")
        then: spec.includePatterns == ["x"] as Set

        when:
        spec.excludeTestsMatching("*barMethod")
        spec.excludeTestsMatching("*.BarTest.*")

        then: spec.excludePatterns == ["*barMethod", "*.BarTest.*"] as Set

        when: spec.setExcludePatterns("y")
        then: spec.excludePatterns == ["y"] as Set

    }

    def "allows configuring by test class and method name"() {
        expect:
        spec.includePatterns.isEmpty()
        spec.excludePatterns.isEmpty()

        when:
        spec.includeTest("acme.FooTest", "bar")
        spec.includeTest("acme.BarTest", null)
        spec.excludeTest("acme.FooTest", "bar1")

        then:
        spec.includePatterns == ["acme.FooTest.bar", "acme.BarTest.*"] as Set
        spec.excludePatterns == ["acme.FooTest.bar1"] as Set
    }

    def "prevents empty names"() {
        when: spec.includeTestsMatching(null)
        then: thrown(InvalidUserDataException)

        when: spec.includeTestsMatching("")
        then: thrown(InvalidUserDataException)

        when: spec.setIncludePatterns("ok", "")
        then: thrown(InvalidUserDataException)
    }

}
