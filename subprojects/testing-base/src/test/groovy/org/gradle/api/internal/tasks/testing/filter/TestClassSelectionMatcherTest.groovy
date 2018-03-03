/*
 * Copyright 2018 the original author or authors.
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

import spock.lang.Specification
import spock.lang.Unroll

class TestClassSelectionMatcherTest extends Specification {

    @Unroll
    def 'can exclude as many classes as possible'() {
        expect:
        new TestClassSelectionMatcher(input, []).maybeMatchClass(fullQualifiedName) == maybeMatch
        new TestClassSelectionMatcher([], input).maybeMatchClass(fullQualifiedName) == maybeMatch

        where:
        input                             | fullQualifiedName    | maybeMatch
        ['.']                             | 'FooTest'            | false
        ['.FooTest.']                     | 'FooTest'            | false
        ['FooTest']                       | 'FooTest'            | true
        ['FooTest']                       | 'org.gradle.FooTest' | true
        ['FooTest']                       | 'org.foo.FooTest'    | true
        ['FooTest']                       | 'BarTest'            | false
        ['FooTest']                       | 'org.gradle.BarTest' | false
        ['FooTest.testMethod']            | 'FooTest'            | true
        ['FooTest.testMethod']            | 'BarTest'            | false
        ['FooTest.testMethod']            | 'org.gradle.FooTest' | true
        ['FooTest.testMethod']            | 'org.gradle.BarTest' | false
        ['org.gradle.FooTest.testMethod'] | 'FooTest'            | false
        ['org.gradle.FooTest.testMethod'] | 'org.gradle.FooTest' | true
        ['org.gradle.FooTest.testMethod'] | 'org.gradle.BarTest' | false
        ['org.foo.FooTest.testMethod']    | 'org.gradle.FooTest' | false
        ['org.foo.FooTest']               | 'org.gradle.FooTest' | false

        ['*FooTest*']                     | 'org.gradle.FooTest' | true
        ['*FooTest*']                     | 'aaa'                | true
        ['*FooTest']                      | 'org.gradle.FooTest' | true
        ['*FooTest']                      | 'FooTest'            | true
        ['*FooTest']                      | 'org.gradle.BarTest' | true // org.gradle.BarTest.testFooTest

        ['FooTest*']                      | 'FooTest'            | true
        ['FooTest*']                      | 'org.gradle.FooTest' | false
        ['FooTest*']                      | 'BarTest'            | false
        ['FooTest*']                      | 'org.gradle.BarTest' | false
        ['org.gradle.FooTest*']           | 'org.gradle.BarTest' | false
        ['FooTest.testMethod*']           | 'FooTest'            | true
        ['FooTest.testMethod*']           | 'org.gradle.FooTest' | false
        ['org.foo.FooTest*']              | 'FooTest'            | false
        ['org.foo.FooTest*']              | 'org.gradle.FooTest' | false
        ['org.foo.*FooTest*']             | 'org.gradle.FooTest' | false
        ['org.foo.*FooTest*']             | 'org.foo.BarTest'    | true // org.foo.BarTest.testFooTest
    }
}
