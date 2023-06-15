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

package org.gradle.api.internal.tasks.testing.junit

import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo
import org.gradle.api.internal.tasks.testing.filter.TestFilterSpec
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.internal.actor.TestActorFactory
import org.gradle.internal.id.LongIdGenerator
import org.gradle.internal.time.Time
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED

class JUnitTestClassProcessorTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())

    def processor = Mock(TestResultProcessor)

    @Subject
    def classProcessor = createProcessor([] as Set, [] as Set, [] as Set, [] as Set, [] as Set)

    JUnitTestClassProcessor createProcessor(Set<String> includeCategories, Set<String> excludeCategories, Set<String> includedTests, Set<String> excludedTests, Set<String> includedTestsCommandLine) {
        def spec = new JUnitSpec(new TestFilterSpec(includedTests, excludedTests, includedTestsCommandLine), includeCategories, excludeCategories, false)
        new JUnitTestClassProcessor(spec, new LongIdGenerator(), new TestActorFactory(), Time.clock())
    }

    void process(Class... clazz) {
        process(clazz*.name)
    }

    void process(Iterable<String> classNames) {
        classProcessor.startProcessing(processor)
        for (String c : classNames) {
            classProcessor.processTestClass(new DefaultTestClassRunInfo(c))
        }
        classProcessor.stop()
    }

    def "executes a JUnit 4 test class"() {
        when:
        process(ATestClass)

        then:
        1 * processor.started({ it.id == 1 && it.name == ATestClass.name && it.className == ATestClass.name }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == "ok" && it.className == ATestClass.name }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == null }) //wondering why result type is null? Failures are notified via failure() method
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes a JUnit 4 test class with ignored test"() {
        when:
        process(ATestClassWithIgnoredMethod)

        then:
        1 * processor.started({it.id == 1}, {it.parentId == null})
        1 * processor.started({ it.id == 2 && it.name == "ignored" && it.className == ATestClassWithIgnoredMethod.name }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == SKIPPED })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes a JUnit 4 test class with failed test assumption"() {
        when:
        process(ATestClassWithFailedTestAssumption)

        then:
        1 * processor.started({it.id == 1}, {it.parentId == null})
        1 * processor.started({ it.id == 2 && it.name == "assumed" && it.className == ATestClassWithFailedTestAssumption.name }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == SKIPPED })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes an ignored JUnit 4 test class"() {
        when:
        process(AnIgnoredTestClass)

        then:
        1 * processor.started({it.id == 1}, {it.parentId == null})
        1 * processor.started({ it.id == 2 && it.name == "ignored2" && it.className == AnIgnoredTestClass.name }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == SKIPPED })
        1 * processor.started({ it.id == 3 && it.name == "ignored" && it.className == AnIgnoredTestClass.name }, { it.parentId == 1 })
        1 * processor.completed(3, { it.resultType == SKIPPED })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes a JUnit 3 test class"() {
        when:
        process(AJunit3TestClass)

        then:
        1 * processor.started({it.id == 1}, {it.parentId == null})
        1 * processor.started({ it.id == 2 && it.name == "testOk" && it.className == AJunit3TestClass.name }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes  multiple test classes"() {
        when:
        process(ATestClass, AJunit3TestClass)

        then:
        1 * processor.started({ it.id == 1 && it.name == ATestClass.name && it.className == ATestClass.name }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == "ok" && it.className == ATestClass.name }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })

        1 * processor.started({ it.id == 3 && it.name == AJunit3TestClass.name && it.className == AJunit3TestClass.name }, { it.parentId == null })
        1 * processor.started({ it.id == 4 && it.name == "testOk" && it.className == AJunit3TestClass.name }, { it.parentId == 3 })
        1 * processor.completed(4, { it.resultType == null })
        1 * processor.completed(3, { it.resultType == null })
        0 * processor._
    }

    def "executes a test class with RunWith annotation"() {
        when:
        process(ATestClassWithRunner)

        then:
        1 * processor.started({ it.id == 1 }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == "broken" && it.className == ATestClassWithRunner.name }, { it.parentId == 1 })
        1 * processor.started({ it.id == 3 && it.name == "ok" && it.className == ATestClassWithRunner.name }, { it.parentId == 1 })
        1 * processor.failure(2, CustomRunner.failure)
        1 * processor.completed(3, { it.resultType == null })
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes a test class with a suite method"() {
        when:
        process(ATestClassWithSuiteMethod)

        then:
        1 * processor.started({ it.id == 1 }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == "testOk" && it.className == AJunit3TestClass.name }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.started({ it.id == 3 && it.name == "testOk" && it.className == BJunit3TestClass.name }, { it.parentId == 1 })
        1 * processor.completed(3, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes a test class with broken before and after method"() {
        when:
        process(ATestClassWithBrokenBeforeAndAfterMethod)

        then:
        1 * processor.started({ it.id == 1 }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == 'test' && it.className == ATestClassWithBrokenBeforeAndAfterMethod.name }, { it.parentId == 1 })
        1 * processor.failure(2, ATestClassWithBrokenBeforeAndAfterMethod.beforeFailure)
        1 * processor.failure(2, ATestClassWithBrokenBeforeAndAfterMethod.afterFailure)
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "#testClass reports failure"() {
        when:
        process(testClass)

        then:
        1 * processor.started({ it.id == 1 }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == testMethodName && it.className == testClass.name }, { it.parentId == 1 })
        1 * processor.failure(2, failure)
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._

        where:
        testClass                             |testMethodName        |failure
        ABrokenTestClass                      |'broken'              |ABrokenTestClass.failure
        ABrokenJunit3TestClass                |'testBroken'          |ABrokenJunit3TestClass.failure
        ATestClassWithBrokenRunner            |'initializationError' |CustomRunnerWithBrokenRunMethod.failure
        ATestClassWithUnconstructibleRunner   |'initializationError' |CustomRunnerWithBrokenConstructor.failure
        ATestClassWithBrokenBeforeClassMethod |'classMethod'         |ATestClassWithBrokenBeforeClassMethod.failure
        ATestClassWithBrokenConstructor       |'test'                |ATestClassWithBrokenConstructor.failure
        ATestClassWithBrokenBeforeMethod      |'test'                |ATestClassWithBrokenBeforeMethod.failure
        ATestClassWithBrokenSuiteMethod       |'initializationError' |ATestClassWithBrokenSuiteMethod.failure
        ATestSetUpWithBrokenSetUp             |AJunit3TestClass.name |ATestSetUpWithBrokenSetUp.failure
    }

    def "executes a test class with runner that breaks after running some tests"() {
        when:
        process(ATestClassWithRunnerThatBreaksAfterRunningSomeTests)

        then:
        1 * processor.started({ it.id == 1 }, { it.parentId == null })

        1 * processor.started({ it.id == 2 && it.name == 'ok1' && it.className == ATestClassWithRunnerThatBreaksAfterRunningSomeTests.name }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == null })

        1 * processor.started({ it.id == 3 && it.name == 'broken' && it.className == ATestClassWithRunnerThatBreaksAfterRunningSomeTests.name }, { it.parentId == 1 })
        1 * processor.failure(3, CustomRunnerWithRunMethodThatBreaksAfterRunningSomeTests.failure)
        1 * processor.completed(3, { it.resultType == null })

        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes a test class which cannot be loaded"() {
        setup:
        String testClassName = 'org.gradle.api.internal.tasks.testing.junit.ATestClassWhichCannotBeLoaded'

        when:
        process([testClassName])

        then:
        1 * processor.started({ it.id == 1 }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == 'pass' && it.className == testClassName }, { it.parentId == 1 })
        1 * processor.failure(2, { it.rawFailure instanceof NoClassDefFoundError})
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes a JUnit 3 test class that renames itself"() {
        when:
        process(AJunit3TestThatRenamesItself)

        then:
        1 * processor.started({ it.id == 1 }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == 'testOk' && it.className == AJunit3TestThatRenamesItself.name }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes specific method"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, [ATestClassWithSeveralMethods.name + ".pass"] as Set, [] as Set, [] as Set)

        when:
        process(ATestClassWithSeveralMethods)

        then:
        1 * processor.started({ it.id == 1 }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == "pass" && it.className == ATestClassWithSeveralMethods.name }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes multiple specific methods"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, [ATestClassWithSeveralMethods.name + ".pass",
                ATestClassWithSeveralMethods.name + ".pass2"] as Set, [] as Set, [] as Set)

        when:
        process(ATestClassWithSeveralMethods)

        then:
        1 * processor.started({ it.name == ATestClassWithSeveralMethods.name }, _)
        1 * processor.started({ it.name == "pass" && it.className == ATestClassWithSeveralMethods.name }, _)
        1 * processor.started({ it.name == "pass2" && it.className == ATestClassWithSeveralMethods.name }, _)
        0 * processor.started(_, _)
    }

    def "executes methods from multiple classes by pattern"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, ["*Methods.*Slowly*"] as Set, [] as Set, [] as Set)

        when:
        process(ATestClassWithSeveralMethods, ATestClassWithSlowMethods, ATestClass)

        then:
        1 * processor.started({ it.name == ATestClassWithSeveralMethods.name }, _)
        1 * processor.started({ it.name == "passSlowly" && it.className == ATestClassWithSeveralMethods.name }, _)
        1 * processor.started({ it.name == "passSlowly2" && it.className == ATestClassWithSeveralMethods.name }, _)
        1 * processor.started({ it.name == ATestClassWithSlowMethods.name }, _)
        1 * processor.started({ it.name == "passSlowly" && it.className == ATestClassWithSlowMethods.name }, _)
        1 * processor.started({ it.name == ATestClass.name }, _)
        0 * processor.started(_, _)
    }

    def "executes all tests for class with test runner that is not filterable when any test description matches"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, [ATestClassWithRunner.name + ".ok"] as Set, [] as Set, [] as Set)

        when:
        process(ATestClassWithRunner)

        then:
        1 * processor.started({ it.id == 1 }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == "broken" && it.className == ATestClassWithRunner.name }, { it.parentId == 1 })
        1 * processor.started({ it.id == 3 && it.name == "ok" && it.className == ATestClassWithRunner.name }, { it.parentId == 1 })
        1 * processor.failure(2, CustomRunner.failure)
        1 * processor.completed(3, { it.resultType == null })
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "does not execute class with test runner that is not filterable when no test description matches"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, [ATestClassWithRunner.name + ".ignoreme"] as Set, [] as Set, [] as Set)

        when:
        process(ATestClassWithRunner)

        then:
        1 * processor.started({ it.id == 1 }, { it.parentId == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes no methods when method name does not match"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, ["does not exist"] as Set, [] as Set, [] as Set)

        when:
        process(ATestClassWithSeveralMethods)

        then:
        1 * processor.started({ it.id == 1 }, { it.parentId == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "executes all tests within a JUnit 3 suite when the suite class name matches"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, ["*ATestClassWithSuiteMethod"] as Set,[] as Set,  [] as Set)

        //Run tests in ATestClassWithSuiteMethod only
        when:
        process(ATestClassWithSuiteMethod, ATestSuite)

        then:
        1 * processor.started({ it.id == 1 && it.className == ATestClassWithSuiteMethod.name }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == "testOk" && it.className == AJunit3TestClass.name }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.started({ it.id == 3 && it.name == "testOk" && it.className == BJunit3TestClass.name }, { it.parentId == 1 })
        1 * processor.completed(3, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        1 * processor.started({ it.id == 4 && it.className == ATestSuite.name }, { it.parentId == null })
        1 * processor.completed(4, { it.resultType == null })
        0 * processor._
    }

    def "executes all tests within a suite when the suite class name matches"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, ["*ATestSuite"] as Set, [] as Set, [] as Set)

        //Run tests in ATestSuite only
        when:
        process(ATestClassWithSuiteMethod, ATestSuite)

        then:
        1 * processor.started({ it.id == 1 && it.className == ATestClassWithSuiteMethod.name }, { it.parentId == null })
        1 * processor.completed(1, { it.resultType == null })
        1 * processor.started({ it.id == 2 && it.className == ATestSuite.name }, { it.parentId == null })
        1 * processor.started({ it.id == 3 && it.name == "ok" && it.className == ATestClass.name }, { it.parentId == 2 })
        1 * processor.completed(3, { it.resultType == null })
        1 * processor.started({ it.id == 4 && it.name == "coolName" && it.className == BTestClass.name }, { it.parentId == 2 })
        1 * processor.completed(4, { it.resultType == null })
        1 * processor.started({ it.id == 5 && it.name == "ok" && it.className == BTestClass.name }, { it.parentId == 2 })
        1 * processor.completed(5, { it.resultType == null })
        1 * processor.completed(2, { it.resultType == null })
        0 * processor._
    }

    def "executes all tests within a custom runner suite class name matches"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, ["*ACustomSuite"] as Set, [] as Set, [] as Set)

        //Run tests in ATestSuite only
        when:
        process(ATestClassWithSuiteMethod, ACustomSuite)

        then:
        1 * processor.started({ it.id == 1 && it.className == ATestClassWithSuiteMethod.name }, { it.parentId == null })
        1 * processor.completed(1, { it.resultType == null })
        1 * processor.started({ it.id == 2 && it.className == ACustomSuite.name }, { it.parentId == null })
        1 * processor.started({ it.id == 3 && it.name == "ok" && it.className == ATestClass.name }, { it.parentId == 2 })
        1 * processor.completed(3, { it.resultType == null })
        1 * processor.started({ it.id == 4 && it.name == "coolName" && it.className == BTestClass.name }, { it.parentId == 2 })
        1 * processor.completed(4, { it.resultType == null })
        1 * processor.started({ it.id == 5 && it.name == "ok" && it.className == BTestClass.name }, { it.parentId == 2 })
        1 * processor.completed(5, { it.resultType == null })
        1 * processor.completed(2, { it.resultType == null })
        0 * processor._
    }

    def "attempting to filter methods on a suite does NOT work"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, ["*ATestSuite.ok*"] as Set, [] as Set, [] as Set)

        //Doesn't run any tests
        when:
        process(ATestClassWithSuiteMethod, ATestSuite)

        then:
        1 * processor.started({ it.id == 1 && it.className == ATestClassWithSuiteMethod.name }, { it.parentId == null })
        1 * processor.completed(1, { it.resultType == null })
        1 * processor.started({ it.id == 2 && it.className == ATestSuite.name }, { it.parentId == null })
        1 * processor.completed(2, { it.resultType == null })
        0 * processor._
    }

    @Issue("GRADLE-3112")
    def "has no errors when dealing with an empty suite"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, ["*AnEmptyTestSuite"] as Set, [] as Set, [] as Set)

        //Run tests in AnEmptyTestSuite (e.g. no tests)
        when:
        process(AnEmptyTestSuite)

        then:
        1 * processor.started({ it.id == 1 && it.className == AnEmptyTestSuite.name }, { it.parentId == null})
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    @Issue("GRADLE-3112")
    def "parameterized tests can be run with a class-level filter"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, ["*AParameterizedTest"] as Set, [] as Set, [] as Set)

        when:
        process(AParameterizedTest)

        then:
        1 * processor.started({ it.id == 1 && it.className == AParameterizedTest.name }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.className == AParameterizedTest.name && it.name == "helpfulTest[0]" }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.started({ it.id == 3 && it.className == AParameterizedTest.name && it.name == "unhelpfulTest[0]" }, { it.parentId == 1 })
        1 * processor.completed(3, { it.resultType == null })
        1 * processor.started({ it.id == 4 && it.className == AParameterizedTest.name && it.name == "helpfulTest[1]" }, { it.parentId == 1 })
        1 * processor.completed(4, { it.resultType == null })
        1 * processor.started({ it.id == 5 && it.className == AParameterizedTest.name && it.name == "unhelpfulTest[1]" }, { it.parentId == 1 })
        1 * processor.completed(5, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    @Issue("GRADLE-3112")
    def "parameterized tests can be filtered by method name"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, ["*AParameterizedTest.helpfulTest*"] as Set, [] as Set, [] as Set)

        when:
        process(AParameterizedTest)

        then:
        1 * processor.started({ it.id == 1 && it.className == AParameterizedTest.name }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.className == AParameterizedTest.name && it.name == "helpfulTest[0]" }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.started({ it.id == 3 && it.className == AParameterizedTest.name && it.name == "helpfulTest[1]" }, { it.parentId == 1 })
        1 * processor.completed(3, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    @Issue("GRADLE-3112")
    def "parameterized tests can be filtered by iteration only."() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, ["*AParameterizedTest.*[1]"] as Set, [] as Set, [] as Set)

        when:
        process(AParameterizedTest)

        then:
        1 * processor.started({ it.id == 1 && it.className == AParameterizedTest.name }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.className == AParameterizedTest.name && it.name == "helpfulTest[1]" }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.started({ it.id == 3 && it.className == AParameterizedTest.name && it.name == "unhelpfulTest[1]" }, { it.parentId == 1 })
        1 * processor.completed(3, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    @Issue("GRADLE-3112")
    def "parameterized tests can be filtered by full method name"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, ["*AParameterizedTest.helpfulTest[1]"] as Set, [] as Set, [] as Set)

        when:
        process(AParameterizedTest)

        then:
        1 * processor.started({ it.id == 1 && it.className == AParameterizedTest.name }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.className == AParameterizedTest.name && it.name == "helpfulTest[1]" }, { it.parentId == 1 })
        1 * processor.completed(2, { it.resultType == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    @Issue("GRADLE-3112")
    def "parameterized tests can be empty"() {
        setup:
        classProcessor = createProcessor([] as Set, [] as Set, ["*AnEmptyParameterizedTest"] as Set, [] as Set, [] as Set)

        when:
        process(AnEmptyParameterizedTest)

        then:
        1 * processor.started({ it.id == 1 && it.className == AnEmptyParameterizedTest.name }, { it.parentId == null })
        1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    def "stopNow should fail on call"() {
        when:
        classProcessor.stopNow()

        then:
        UnsupportedOperationException uoe = thrown()
    }
}
