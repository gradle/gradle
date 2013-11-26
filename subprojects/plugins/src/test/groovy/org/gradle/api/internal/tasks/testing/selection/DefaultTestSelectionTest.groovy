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

package org.gradle.api.internal.tasks.testing.selection

import org.gradle.api.tasks.testing.TestSelectionSpec
import spock.lang.Specification

import static org.gradle.util.Matchers.isSerializable
import static org.hamcrest.MatcherAssert.assertThat

class DefaultTestSelectionTest extends Specification {

    def selection = new DefaultTestSelection()

    def "configures included tests"() {
        expect:
        selection.includedTests.empty

        when:
        selection.includedTests = [new DefaultTestSelectionSpec("hey", "Joe!")]
        selection.includedTests = [new DefaultTestSelectionSpec("*", "*")]
        selection.includeTest("Foo", "bar")

        then:
        selection.includedTests == [new DefaultTestSelectionSpec("*", "*"), new DefaultTestSelectionSpec("Foo", "bar")] as Set
    }

    def "included tests are configurable by instances of TestSelectionSpec"() {
        def spec = Stub(TestSelectionSpec) {
            getMethodPattern() >> "foo"
            getClassPattern() >> "FooTest"
        }

        when:
        selection.setIncludedTests(spec)

        then:
        selection.includedTests == [new DefaultTestSelectionSpec("FooTest", "foo")] as Set
        selection.includedTests.each { assertThat(it, isSerializable()) }
    }
}
