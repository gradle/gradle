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

package org.gradle.api.internal.tasks.testing.testng

import org.gradle.api.GradleException
import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.filter.TestFilterSpec
import org.gradle.api.tasks.testing.TestResult.ResultType
import org.gradle.internal.actor.TestActorFactory
import org.gradle.internal.id.LongIdGenerator
import org.gradle.internal.time.Time
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject

class TestNGTestClassProcessorTest extends Specification {

    @Rule TestNameTestDirectoryProvider dir = new TestNameTestDirectoryProvider(getClass())

    def processor = Mock(TestResultProcessor)
    def filterSpec = Spy(new TestFilterSpec([] as Set, [] as Set, [] as Set))
    def spec = Spy(new TestNGSpec(
        filterSpec,
        "Gradle suite", "Gradle test", null, -1, false,
        [] as Set, [] as Set, [] as Set,
        TestNGTestClassProcessor.DEFAULT_CONFIG_FAILURE_POLICY, false, false, false))

    @Subject classProcessor = new TestNGTestClassProcessor(dir.testDirectory, spec, [], new LongIdGenerator(), Time.clock(), new TestActorFactory())

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

    void "executes the test class"() {
        when: process(ATestNGClass)

        then: 1 * processor.started({ it.id == 1 && it.name == 'Gradle suite' && it.className == null }, { it.parentId == null })
        then: 1 * processor.started({ it.id == 2 && it.name == 'Gradle test' && it.className == null }, { it.parentId == 1 })
        then: 1 * processor.started({ it.id == 3 && it.name == 'ok' && it.className == ATestNGClass.name }, { it.parentId == 2 })
        then: 1 * processor.completed(3, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(2, { it.resultType == null })
        then: 1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    void "executes factory test class"() {
        when:
        process(ATestNGFactoryClass)

        then: 1 * processor.started({ it.id == 1 && it.name == 'Gradle suite' && it.className == null }, { it.parentId == null })
        then: 1 * processor.started({ it.id == 2 && it.name == 'Gradle test' && it.className == null }, { it.parentId == 1 })
        then: 1 * processor.started({ it.id == 3 && it.name == 'ok' && it.className == ATestNGClass.name }, { it.parentId == 2 })
        then: 1 * processor.completed(3, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(2, { it.resultType == null })
        then: 1 * processor.completed(1, { it.resultType == null })

        0 * processor._
    }

    void "executes selected included method"() {
        filterSpec.getIncludedTests() >> [ATestNGClassWithManyMethods.name + ".another"]

        when: process(ATestNGClassWithManyMethods)

        then: 1 * processor.started({ it.id == 1 && it.name == 'Gradle suite' && it.className == null }, { it.parentId == null })
        then: 1 * processor.started({ it.id == 2 && it.name == 'Gradle test' && it.className == null }, { it.parentId == 1 })
        then: 1 * processor.started({ it.id == 3 && it.name == 'another' && it.className == ATestNGClassWithManyMethods.name }, { it.parentId == 2 })
        then: 1 * processor.completed(3, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(2, { it.resultType == null })
        then: 1 * processor.completed(1, { it.resultType == null })

        0 * processor._
    }

    void "executes multiple included methods"() {
        filterSpec.getIncludedTests() >> [ATestNGClassWithManyMethods.name + ".another", ATestNGClassWithManyMethods.name + ".yetAnother"]

        when: process(ATestNGClassWithManyMethods)

        then:
        1 * processor.started({ it.id == 1 && it.name == 'Gradle suite' && it.className == null }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == 'Gradle test' && it.className == null }, { it.parentId == 1 })
        1 * processor.started({ it.id == 3 && it.name == 'another' && it.className == ATestNGClassWithManyMethods.name }, { it.parentId == 2 })
        1 * processor.started({ it.id == 4 && it.name == 'yetAnother' && it.className == ATestNGClassWithManyMethods.name }, { it.parentId == 2 })
        0 * processor.started(_, _)
    }

    void "executes methods from multiple classes by pattern"() {
        filterSpec.getIncludedTests() >> ["*Methods.ok*"]

        when: process(ATestNGClassWithManyMethods)

        then:
        1 * processor.started({ it.id == 1 && it.name == 'Gradle suite' }, _)
        1 * processor.started({ it.id == 2 && it.name == 'Gradle test' }, _)
        1 * processor.started({ it.name == 'ok' && it.className == ATestNGClassWithManyMethods.name }, { it.parentId == 2 })
        1 * processor.started({ it.name == 'ok2' && it.className == ATestNGClassWithManyMethods.name }, { it.parentId == 2 })
        0 * processor.started(_, _)
    }

    void "executes no tests if none of the included test methods match"() {
        filterSpec.getIncludedTests() >> [ATestNGClassWithManyMethods.name + "does not exist"]

        when: process(ATestNGClassWithManyMethods)

        then: 1 * processor.started({ it.id == 1 && it.className == null  && it.name == 'Gradle suite' }, { it.parentId == null })
        then: 1 * processor.started({ it.id == 2 && it.className == null && it.name == 'Gradle test' }, { it.parentId == 1 })
        then: 1 * processor.completed(2, { it.resultType == null })
        then: 1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    void "executes test with expected exception"() {
        when: process(ATestNGClassWithExpectedException)

        then: 1 * processor.started({ it.id == 1 } , _)
        then: 1 * processor.started({ it.id == 2 } , _)
        then: 1 * processor.started({ it.name == 'ok' && it.className == ATestNGClassWithExpectedException.name }, { it.parentId == 2 })
        then: 1 * processor.completed(3, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(2, { it.resultType == null })
        then: 1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    void "executes test with broken setup"() {
        when: process(ATestNGClassWithBrokenSetupMethod)

        then: 1 * processor.started({ it.id == 1 } , _)
        then: 1 * processor.started({ it.id == 2 } , _)
        then: 1 * processor.started({ it.name == 'beforeMethod' && it.className == ATestNGClassWithBrokenSetupMethod.name }, _)
        then: 1 * processor.failure(3, ATestNGClassWithBrokenSetupMethod.failure)
        then: 1 * processor.completed(3, { it.resultType == ResultType.FAILURE })

        then: 1 * processor.started({ it.name == 'test' && it.className == ATestNGClassWithBrokenSetupMethod.name }, _)
        then: 1 * processor.completed(4, { it.resultType == ResultType.SKIPPED})

        then: 1 * processor.completed(2, { it.resultType == null})
        then: 1 * processor.completed(1, { it.resultType == null})

        0 * processor._
    }

    void "executes test class with dependency method"() {
        when: process(ATestNGClassWithBrokenDependencyMethod)

        then: 1 * processor.started({ it.id == 1 } , _)
        then: 1 * processor.started({ it.id == 2 } , _)
        then: 1 * processor.started({ it.name == 'beforeMethod' && it.className == ATestNGClassWithBrokenDependencyMethod.name }, _)

        then: 1 * processor.failure(3, ATestNGClassWithBrokenDependencyMethod.failure)
        then: 1 * processor.completed(3, { it.resultType == ResultType.FAILURE })

        then: 1 * processor.started({ it.name == 'test' && it.className == ATestNGClassWithBrokenDependencyMethod.name }, _)
        then: 1 * processor.completed(4, { it.resultType == ResultType.SKIPPED})

        then: 1 * processor.completed(2, { it.resultType == null})
        then: 1 * processor.completed(1, { it.resultType == null})

        0 * processor._
    }

    void "includes and excludes groups"() {
        given:
        _ * spec.getIncludeGroups() >> ['group1', 'group2']
        _ * spec.getExcludeGroups() >> ['group3']

        when: process(ATestNGClassWithGroups)

        then:
        1 * processor.started({ it.id == 1 } , _)
        1 * processor.started({ it.id == 2 } , _)
        1 * processor.started({ it.name == 'group1' && it.className == ATestNGClassWithGroups.name }, _)
        1 * processor.started({ it.name == 'group2' && it.className == ATestNGClassWithGroups.name }, _)
        4 * processor.completed(_, _)
        0 * processor._
    }

    @Ignore //not implemented yet
    void "executes class with broken constructor"() {
        when: process(ATestNGClassWithBrokenConstructor)

        then:
        //below needs to revisited when we attempt to fix the problem
        //e.g. decide what's the behavior we want in this scenario
        1 * processor.started({ it.id == 1} , _)
        1 * processor.started({ it.name == 'initializationError' && it.className == ATestNGClassWithBrokenConstructor.name }, _)
        1 * processor.failure(1, ATestNGClassWithBrokenConstructor.failure)
        1 * processor.completed(1, { it.resultType == ResultType.FAILURE})
        0 * processor._
    }

    void "fails early for unknown test class"() {
        classProcessor.startProcessing(processor)

        when:
        classProcessor.processTestClass(new DefaultTestClassRunInfo('unknown'))

        then:
        def ex = thrown(GradleException)
        ex.message == "Could not load test class \'unknown\'."
    }

    void "before and after methods are not triggered when all tests from a class are filtered"() {
        filterSpec.getIncludedTests() >> [ATestNGClass.name]

        when:
        process(ATestNGClass, ATestNGClassWithBeforeAndAfter) //the latter is not matched

        then:
        then: 1 * processor.started({ it.id == 1 && it.name == 'Gradle suite' && it.className == null }, _)
        then: 1 * processor.started({ it.id == 2 && it.name == 'Gradle test' && it.className == null }, _)
        then: 1 * processor.started({ it.id == 3 && it.name == 'ok' && it.className == ATestNGClass.name }, _)
        then: 1 * processor.completed(3, _)
        then: 1 * processor.completed(2, _)
        then: 1 * processor.completed(1, _)
        0 * processor._
    }

    void "custom test listeners can change test status"() {
        spec.listeners << FailSkippedTestsListener.class.name

        when: process(ATestNGClassWithSkippedTest)

        then: 1 * processor.completed(_, { it.resultType == ResultType.FAILURE})
    }

    void "executes test from suite"() {
        def suite = dir.file("suite.xml") << """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
<suite name="AwesomeSuite">
  <test name="AwesomeTest">
    <classes>
      <class name="${ATestNGClass.name}"/>
    </classes>
  </test>
</suite>"""
        classProcessor = new TestNGTestClassProcessor(dir.testDirectory, spec, [suite], new LongIdGenerator(), Time.clock(), new TestActorFactory())

        when:
        classProcessor.startProcessing(processor)
        classProcessor.stop()

        then: 1 * processor.started({ it.id == 1 && it.name == 'AwesomeSuite' && it.className == null }, { it.parentId == null })
        then: 1 * processor.started({ it.id == 2 && it.name == 'AwesomeTest' && it.className == null }, { it.parentId == 1 })
        then: 1 * processor.started({ it.id == 3 && it.name == 'ok' && it.className == ATestNGClass.name }, { it.parentId == 2 })
        then: 1 * processor.completed(3, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(2, { it.resultType == null })
        then: 1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    void "executes multiple suites and tests"() {
        def suite1 = dir.file("suite1.xml") << """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
<suite name="suite 1">
  <test name="test 1">
    <classes>
      <class name="${ATestNGClass.name}"/>
    </classes>
  </test>
  <test name="test 2">
    <classes>
      <class name="${ATestNGClass.name}"/>
    </classes>
  </test>
</suite>"""
        def suite2 = dir.file("suite2.xml") << """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
<suite name="suite 2">
  <test name="test 1">
    <classes>
      <class name="${ATestNGClass.name}"/>
    </classes>
  </test>
  <test name="test 2">
    <classes>
      <class name="${ATestNGClass.name}"/>
    </classes>
  </test>
</suite>"""
        classProcessor = new TestNGTestClassProcessor(dir.testDirectory, spec, [suite1, suite2], new LongIdGenerator(), Time.clock(), new TestActorFactory())

        when:
        classProcessor.startProcessing(processor)
        classProcessor.stop()

        then: 1 * processor.started({ it.id == 1 && it.name == 'suite 1' && it.className == null }, { it.parentId == null })
        then: 1 * processor.started({ it.id == 2 && it.name == 'test 1' && it.className == null }, { it.parentId == 1 })
        then: 1 * processor.started({ it.id == 3 && it.name == 'ok' && it.className == ATestNGClass.name }, { it.parentId == 2 })
        then: 1 * processor.completed(3, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(2, { it.resultType == null })

        then: 1 * processor.started({ it.id == 4 && it.name == 'test 2' && it.className == null }, { it.parentId == 1 })
        then: 1 * processor.started({ it.id == 5 && it.name == 'ok' && it.className == ATestNGClass.name }, { it.parentId == 4 })
        then: 1 * processor.completed(5, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(4, { it.resultType == null })
        then: 1 * processor.completed(1, { it.resultType == null })

        then: 1 * processor.started({ it.id == 6 && it.name == 'suite 2' && it.className == null }, { it.parentId == null })
        then: 1 * processor.started({ it.id == 7 && it.name == 'test 1' && it.className == null }, { it.parentId == 6 })
        then: 1 * processor.started({ it.id == 8 && it.name == 'ok' && it.className == ATestNGClass.name }, { it.parentId == 7 })
        then: 1 * processor.completed(8, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(7, { it.resultType == null })

        then: 1 * processor.started({ it.id == 9 && it.name == 'test 2' && it.className == null }, { it.parentId == 6 })
        then: 1 * processor.started({ it.id == 10 && it.name == 'ok' && it.className == ATestNGClass.name }, { it.parentId == 9 })
        then: 1 * processor.completed(10, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(9, { it.resultType == null })
        then: 1 * processor.completed(6, { it.resultType == null })

        0 * processor._
    }

    def "stopNow should fail on call"() {
        when: classProcessor.stopNow()

        then:
        UnsupportedOperationException uoe = thrown()
    }
}
