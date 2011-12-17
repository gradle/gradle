/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.results

import org.gradle.api.internal.tasks.testing.TestSuiteExecutionException
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.slf4j.Logger
import spock.lang.Specification
import static org.junit.Assert.assertTrue

public class TestSummaryListenerTest extends Specification {
    
    def logger = Mock(Logger.class)
    def failure = new RuntimeException()
    def listener = new TestSummaryListener(logger)

    def "logs successful tests"() {
        when:
        listener.afterTest(test('<test>'), result(TestResult.ResultType.SUCCESS))
        then:
        1 * logger.info('{} PASSED', '<test>')
        0 * logger._
    }

    def "logs skipped tests"() {
        when:
        listener.afterTest(test('<test>'), result(TestResult.ResultType.SKIPPED))
        then:
        1 * logger.info('{} SKIPPED', '<test>')
        0 * logger._
    }

    def "logs failed test execution"() {
        when:
        listener.afterTest(test('<test>', '<class>'), result(TestResult.ResultType.FAILURE))
        then:
        1 * logger.info('{} FAILED: {}', '<test>', failure)
        1 * logger.error('Test {} FAILED', '<class>')
        0 * logger._
    }

    def "logs failed test execution when test has no class"() {
        when:
        listener.afterTest(test('<test>'), result(TestResult.ResultType.FAILURE))
        then:
        1 * logger.error('{} FAILED: {}', '<test>', failure)
        0 * logger._
    }

    def "logs failed suite execution"() {
        when:
        listener.afterSuite(test('<test>', '<class>'), result(TestResult.ResultType.FAILURE))
        then:
        1 * logger.info('{} FAILED: {}', '<test>', failure)
        1 * logger.error('Test {} FAILED', '<class>')
        0 * logger._
    }

    def "logs Failed Suite Execution When Suite Has No Class"() {
        when:
        listener.afterSuite(test('<test>'), result(TestResult.ResultType.FAILURE))
        then:
        1 * logger.error('{} FAILED: {}', '<test>', failure)
        0 * logger._
    }

    def "logs Failed Suite Execution When Suite Has No Exception"() {
        expect:
        listener.afterSuite(test('<test>'), result(TestResult.ResultType.FAILURE, null))
    }

    def "logs Suite Internal Exception"() {
        given:
        def failure = new TestSuiteExecutionException('broken', new RuntimeException())
        when:
        listener.afterSuite(test('<test>'), result(TestResult.ResultType.FAILURE, failure))
        then:
        1 * logger.error('Execution for <test> FAILED', failure)
        0 * logger._
    }

    def "does Not Log Failed Class More Than Once"() {
        when:
        listener.afterTest(test('<test1>', '<class>'), result(TestResult.ResultType.FAILURE))
        listener.afterTest(test('<test2>', '<class>'), result(TestResult.ResultType.FAILURE))
        listener.afterSuite(test('<test3>', '<class>'), result(TestResult.ResultType.FAILURE))
        then:
        1 * logger.error('Test {} FAILED', '<class>')
    }

    def "uses Root Suite Results To Determine If Tests Has Failed"() {
        listener.afterSuite(test('<test>', null, null), result(TestResult.ResultType.FAILURE, null, 3, 5))
        assertTrue(listener.hadFailures())
    }

    private TestResult result(TestResult.ResultType type, Throwable failure = this.failure, long failures = 0, long total = 0) {
        return [getResultType: {-> type}, getException: {-> failure}, getTestCount: {-> total}, getFailedTestCount: {-> failures}] as TestResult
    }

    private TestDescriptor test(String name, String className = null, TestDescriptor parent = [:] as TestDescriptor) {
        return [toString: {-> name}, getClassName: {-> className}, getParent: {-> parent}] as TestDescriptor
    }
}

