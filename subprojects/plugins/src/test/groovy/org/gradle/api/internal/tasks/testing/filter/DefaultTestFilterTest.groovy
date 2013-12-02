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
        expect: spec.includedTests.isEmpty()

        when:
        spec.includeTest("*fooMethod")
        spec.includeTest("*.FooTest.*")

        then: spec.includedTests == ["*fooMethod", "*.FooTest.*"] as Set

        when: spec.setIncludedTests("x")

        then: spec.includedTests == ["x"] as Set
    }

    def "prevents empty names"() {
        when: spec.includeTest(null)
        then: thrown(InvalidUserDataException)

        when: spec.includeTest("")
        then: thrown(InvalidUserDataException)

        when: spec.setIncludedTests("ok", "")
        then: thrown(InvalidUserDataException)
    }

}
