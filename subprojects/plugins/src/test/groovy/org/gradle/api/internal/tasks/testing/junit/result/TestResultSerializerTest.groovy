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
package org.gradle.api.internal.tasks.testing.junit.result

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import org.gradle.api.tasks.testing.TestResult
import org.gradle.messaging.remote.internal.PlaceholderException

class TestResultSerializerTest extends Specification {
    @Rule
    private TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider()
    final TestResultSerializer serializer = new TestResultSerializer()

    def "can write and read results"() {
        def results = [:]
        def class1 = new TestClassResult(1234)
        def failure = new RuntimeException("broken")
        def method1 = new TestMethodResult("method1", TestResult.ResultType.SUCCESS, 100, 2300, [])
        def method2 = new TestMethodResult("method2", TestResult.ResultType.FAILURE, 200, 2700, [failure])
        class1.add(method1)
        class1.add(method2)
        def class2 = new TestClassResult(5678)
        results['Class1'] = class1
        results['Class2'] = class2

        when:
        def read = serialize(results)

        then:
        read.size() == 2
        def readClass1 = read['Class1']
        readClass1.startTime == 1234
        readClass1.results.size() == 2

        def readMethod1 = readClass1.results[0]
        readMethod1.name == 'method1'
        readMethod1.resultType == TestResult.ResultType.SUCCESS
        readMethod1.duration == 100
        readMethod1.endTime == 2300
        readMethod1.exceptions.empty

        def readMethod2 = readClass1.results[1]
        readMethod2.name == 'method2'
        readMethod2.resultType == TestResult.ResultType.FAILURE
        readMethod2.duration == 200
        readMethod2.endTime == 2700
        readMethod2.exceptions.size() == 1
        readMethod2.exceptions[0].class == failure.class
        readMethod2.exceptions[0].message == failure.message
        readMethod2.exceptions[0].stackTrace == failure.stackTrace

        def readClass2 = read['Class2']
        readClass2.startTime == 5678
        readClass2.results.empty
    }

    def "can write and read exceptions that are not serializable"() {
        def results = [:]
        def class1 = new TestClassResult(1234)
        def failure = new RuntimeException("broken") {
            final Object field = new Object()
        }
        def method1 = new TestMethodResult("method1", TestResult.ResultType.FAILURE, 200, 2700, [failure])
        class1.add(method1)
        results['Class1'] = class1

        when:
        def read = serialize(results)

        then:
        read.size() == 1
        def readClass1 = read['Class1']
        def readMethod1 = readClass1.results[0]
        readMethod1.exceptions.size() == 1
        readMethod1.exceptions[0].class == PlaceholderException.class
        readMethod1.exceptions[0].message == failure.message
        readMethod1.exceptions[0].toString() == failure.toString()
        readMethod1.exceptions[0].stackTrace == failure.stackTrace
    }

    Map<String, TestClassResult> serialize(Map<String, TestClassResult> results) {
        def dir = tmp.createDir("results")
        serializer.write(results, dir)
        return serializer.read(dir)
    }
}
