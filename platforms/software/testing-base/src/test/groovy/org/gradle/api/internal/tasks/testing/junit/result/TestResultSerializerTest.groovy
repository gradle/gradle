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


import org.gradle.api.tasks.testing.TestResult
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class TestResultSerializerTest extends Specification {
    @Rule
    private TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())

    def "can write and read results"() {
        def class1 = PersistentTestResult.builder()
            .name('Class1')
            .displayName('Class1')
            .startTime(1234)
            .endTime(5678)
            .resultType(TestResult.ResultType.SUCCESS)
            .build()
        def method1 = PersistentTestResult.builder()
            .name("method1")
            .displayName("method1")
            .startTime(2200)
            .endTime(2300)
            .resultType(TestResult.ResultType.SUCCESS)
            .build()
        def method2 = PersistentTestResult.builder()
            .name("method2")
            .displayName("method2")
            .startTime(2500)
            .endTime(2700)
            .resultType(TestResult.ResultType.FAILURE)
            .addFailure(new PersistentTestFailure("message", "stack-trace", "ExceptionType"))
            .build()
        def class2 = PersistentTestResult.builder()
            .name('Class2')
            .displayName('Class2')
            .startTime(5678)
            .endTime(5678)
            .resultType(TestResult.ResultType.SUCCESS)
            .build()

        when:
        def read = serialize(new PersistentTestResultTree(
            0, PersistentTestResult.builder().name("root").displayName("root").startTime(0).endTime(0).resultType(TestResult.ResultType.SUCCESS).build(),
            [
                new PersistentTestResultTree(
                    1, class1,
                    [
                        new PersistentTestResultTree(2, method1, []),
                        new PersistentTestResultTree(3, method2, []),
                    ]
                ),
                new PersistentTestResultTree(4, class2, []),
            ]
        ))

        then:
        read.children.size() == 2
        def readClass1 = read.children[0]
        readClass1.result.name == 'Class1'
        readClass1.result.startTime == 1234
        readClass1.children.size() == 2

        def readMethod1 = readClass1.children[0]
        readMethod1.result.name == 'method1'
        readMethod1.result.resultType == TestResult.ResultType.SUCCESS
        readMethod1.result.startTime == 2200
        readMethod1.result.endTime == 2300
        readMethod1.result.failures.empty

        def readMethod2 = readClass1.children[1]
        readMethod2.result.name == 'method2'
        readMethod2.result.resultType == TestResult.ResultType.FAILURE
        readMethod2.result.startTime == 2500
        readMethod2.result.endTime == 2700
        readMethod2.result.failures.size() == 1
        readMethod2.result.failures[0].exceptionType == "ExceptionType"
        readMethod2.result.failures[0].message == "message"
        readMethod2.result.failures[0].stackTrace == "stack-trace"

        def readClass2 = read.children[1]
        readClass2.result.name == 'Class2'
        readClass2.result.startTime == 5678
        readClass2.children.empty
    }

    PersistentTestResultTree serialize(PersistentTestResultTree results) {
        def serializer = new TestResultSerializer(tmp.createDir("results"))
        serializer.write(results)
        return serializer.read(TestResultSerializer.VersionMismatchAction.THROW_EXCEPTION)
    }
}
