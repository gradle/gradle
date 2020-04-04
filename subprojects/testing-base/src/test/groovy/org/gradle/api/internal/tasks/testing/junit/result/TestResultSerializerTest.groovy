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

import org.gradle.api.Action
import org.gradle.api.tasks.testing.TestResult
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class TestResultSerializerTest extends Specification {
    @Rule
    private TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())

    def "can write and read results"() {
        def class1 = new TestClassResult(1, 'Class1', 1234)
        def method1 = new TestMethodResult(1, "method1", TestResult.ResultType.SUCCESS, 100, 2300)
        def method2 = new TestMethodResult(2, "method2", TestResult.ResultType.FAILURE, 200, 2700).addFailure("message", "stack-trace", "ExceptionType")
        class1.add(method1)
        class1.add(method2)
        def class2 = new TestClassResult(2, 'Class2', 5678)
        def results = [class1, class2]

        when:
        def read = serialize(results)

        then:
        read.size() == 2
        def readClass1 = read[0]
        readClass1.className == 'Class1'
        readClass1.startTime == 1234
        readClass1.results.size() == 2

        def readMethod1 = readClass1.results[0]
        readMethod1.name == 'method1'
        readMethod1.resultType == TestResult.ResultType.SUCCESS
        readMethod1.duration == 100
        readMethod1.endTime == 2300
        readMethod1.failures.empty

        def readMethod2 = readClass1.results[1]
        readMethod2.name == 'method2'
        readMethod2.resultType == TestResult.ResultType.FAILURE
        readMethod2.duration == 200
        readMethod2.endTime == 2700
        readMethod2.failures.size() == 1
        readMethod2.failures[0].exceptionType == "ExceptionType"
        readMethod2.failures[0].message == "message"
        readMethod2.failures[0].stackTrace == "stack-trace"

        def readClass2 = read[1]
        readClass2.className == 'Class2'
        readClass2.startTime == 5678
        readClass2.results.empty
    }

    List<TestClassResult> serialize(Collection<TestClassResult> results) {
        def serializer = new TestResultSerializer(tmp.createDir("results"))
        serializer.write(results)
        def result = []
        serializer.read({ result << it } as Action)
        return result
    }
}
