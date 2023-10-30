/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.report

import spock.lang.Specification

class TestResultTest extends Specification {
    def canOrderResultsByClassNameAndTestName() {
        ClassTestResults class1 = Mock()
        _ * class1.name >> 'name'
        ClassTestResults class2 = Mock()
        _ * class2.name >> 'a'
        ClassTestResults class3 = Mock()
        _ * class3.name >> 'z'

        TestResult result = new TestResult('name', 0, class1)
        TestResult smallerClass = new TestResult('name', 0, class2)
        TestResult largerClass = new TestResult('name', 0, class3)
        TestResult smallerName = new TestResult('a', 0, class1)
        TestResult largerName = new TestResult('z', 0, class1)

        expect:
        [result, largerName, smallerClass, largerClass, smallerName].sort() == [smallerClass, smallerName, result, largerName, largerClass]
    }

    def doesNotDiscardDuplicatesWhenSorting() {
        ClassTestResults class1 = Mock()
        _ * class1.name >> 'name'

        TestResult result = new TestResult('name', 0, class1)
        TestResult equalResult = new TestResult('name', 0, class1)

        expect:
        def r = [result, equalResult] as SortedSet
        r.size() == 2
    }
}
