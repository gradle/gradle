/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.test.xctest.internal.execution

import spock.lang.Specification
import spock.lang.Subject

@Subject(XCTestSelection)
class XCTestSelectionTest extends Specification {
    def "includes all tests when no filter provided"() {
        expect:
        select().includedTests == [XCTestSelection.INCLUDE_ALL_TESTS]
    }

    def "throws IllegalArgumentException when filter has more than two dots"() {
        when:
        select('more.than.two.dots')

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "'more.than.two.dots' is an invalid pattern. Patterns should have one or two dots."
    }

    def "can use filters with two dots or fewer"() {
        when:
        select('one.dot', 'has.two.dots', 'ModuleName')

        then:
        noExceptionThrown()
    }

    def "converts second dot into slash"() {
        expect:
        select('one.dot', 'has.two.dots').includedTests == ['one.dot', 'has.two/dots']
    }

    def "ignores wildcard to select all tests from module"() {
        expect:
        select('ModuleName.*').includedTests == ['ModuleName.*']
    }

    def "can use wildcard to select all test case from suite"() {
        expect:
        select('ModuleName.suite.*').includedTests == ['ModuleName.suite']
    }

    def "ignores textual duplicate filters"() {
        expect:
        select('a.b.c', 'a.b.c').includedTests == ['a.b/c']
    }

    def "ignores conceptual duplicate filters"() {
        expect:
        select('a.b.*', 'a.b.c', 'a.d.e').includedTests == ['a.b', 'a.d/e']
        select('a.b.c', 'a.d.e', 'a.b.*').includedTests == ['a.d/e', 'a.b']
        select('a.b', 'a.b.c', 'a.d.e').includedTests == ['a.b', 'a.d/e']
        select('a.b.c', 'a.d.e', 'a.b').includedTests == ['a.d/e', 'a.b']
    }

    def "conserve order of filters"() {
        expect:
        select('a.1', 'a.2', 'a.3', 'a.4').includedTests == ['a.1', 'a.2', 'a.3', 'a.4']
    }

    def "throws IllegalArgumentException when filter contains forward slash [#testFilter]"() {
        when:
        select(testFilter)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "'$testFilter' is an invalid pattern. Patterns cannot contain forward slash."

        where:
        testFilter << ['/abc', 'a/bc', 'ab/c', 'a/b/c', 'a/bc', 'a.b/c']
    }

    def "leaves filter as-is when filter has tailing dot"() {
        expect:
        select('a.b.').includedTests == ['a.b.']
    }

    private static XCTestSelection select(String... commandLinePattern) {
        new XCTestSelection([], Arrays.asList(commandLinePattern))
    }
}
