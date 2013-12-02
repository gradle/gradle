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
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.tasks.testing.TestResult.ResultType
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.internal.id.LongIdGenerator
import org.gradle.logging.StandardOutputRedirector
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.testng.annotations.*
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Subject

class TestNGTestClassProcessorTest extends Specification {

    @Rule TestNameTestDirectoryProvider reportDir = new TestNameTestDirectoryProvider()

    def processor = Mock(TestResultProcessor)

    def options = Spy(TestNGSpec, constructorArgs:[new TestNGOptions(reportDir.testDirectory), new DefaultTestFilter()])

    @Subject classProcessor = new TestNGTestClassProcessor(reportDir.testDirectory, options, [], new LongIdGenerator(), {} as StandardOutputRedirector)

    void process(Class ... clazz) {
        classProcessor.startProcessing(processor)
        for (String c : clazz*.name) {
            classProcessor.processTestClass(new DefaultTestClassRunInfo(c))
        }
        classProcessor.stop()
    }

    void "executes the test class"() {
        when: process(ATestNGClass)

        then: 1 * processor.started({ it.id == 1 && it.name == 'Gradle test' && it.className == null }, { it.parentId == null })
        then: 1 * processor.started({ it.id == 2 && it.name == 'ok' && it.className == ATestNGClass.name }, { it.parentId == 1 })
        then: 1 * processor.completed(2, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    void "executes factory test class"() {
        when:
        process(ATestNGFactoryClass)

        then: 1 * processor.started({ it.name == 'Gradle test' && it.className == null }, { it.parentId == null })
        then: 1 * processor.started({ it.name == 'ok' && it.className == ATestNGClass.name }, _ as TestStartEvent)
        then: 1 * processor.completed(2, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(1, { it.resultType == null })

        0 * processor._
    }

    void "executes selected included method"() {
        options.getIncludedTests() >> [ATestNGClassWithManyMethods.name + ".another"]

        when: process(ATestNGClassWithManyMethods)

        then: 1 * processor.started({ it.id == 1 && it.name == 'Gradle test' && it.className == null }, { it.parentId == null })
        then: 1 * processor.started({ it.id == 2 && it.name == 'another' && it.className == ATestNGClassWithManyMethods.name }, { it.parentId == 1 })
        then: 1 * processor.completed(2, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(1, { it.resultType == null })

        0 * processor._
    }

    void "executes multiple included methods"() {
        options.getIncludedTests() >> [ATestNGClassWithManyMethods.name + ".another", ATestNGClassWithManyMethods.name + ".yetAnother"]

        when: process(ATestNGClassWithManyMethods)

        then:
        1 * processor.started({ it.id == 1 && it.name == 'Gradle test' && it.className == null }, { it.parentId == null })
        1 * processor.started({ it.id == 2 && it.name == 'another' && it.className == ATestNGClassWithManyMethods.name }, { it.parentId == 1 })
        1 * processor.started({ it.id == 3 && it.name == 'yetAnother' && it.className == ATestNGClassWithManyMethods.name }, { it.parentId == 1 })
        0 * processor.started(_, _)
    }

    void "executes methods from multiple classes by pattern"() {
        options.getIncludedTests() >> ["*Methods.ok*"]

        when: process(ATestNGClassWithManyMethods)

        then:
        1 * processor.started({ it.id == 1 && it.name == 'Gradle test' }, _)
        1 * processor.started({ it.name == 'ok' && it.className == ATestNGClassWithManyMethods.name }, { it.parentId == 1 })
        1 * processor.started({ it.name == 'ok2' && it.className == ATestNGClassWithManyMethods.name }, { it.parentId == 1 })
        0 * processor.started(_, _)
    }

    void "executes not tests if none of the included test methods match"() {
        options.getIncludedTests() >> [ATestNGClassWithManyMethods.name + "does not exist"]

        when: process(ATestNGClassWithManyMethods)

        then: 1 * processor.started({ it.id == 1 && it.className == null }, { it.parentId == null })
        then: 1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    void "executes test with expected exception"() {
        when: process(ATestNGClassWithExpectedException)

        then: 1 * processor.started({ it.id == 1} , _)
        then: 1 * processor.started({ it.name == 'ok' && it.className == ATestNGClassWithExpectedException.name }, _)
        then: 1 * processor.completed(2, { it.resultType == ResultType.SUCCESS })
        then: 1 * processor.completed(1, { it.resultType == null })
        0 * processor._
    }

    void "executes test with broken setup"() {
        when: process(ATestNGClassWithBrokenSetupMethod)

        then: 1 * processor.started({ it.id == 1} , _)
        then: 1 * processor.started({ it.name == 'beforeMethod' && it.className == ATestNGClassWithBrokenSetupMethod.name }, _)
        then: 1 * processor.failure(2, ATestNGClassWithBrokenSetupMethod.failure)
        then: 1 * processor.completed(2, { it.resultType == ResultType.FAILURE })

        then: 1 * processor.started({ it.name == 'test' && it.className == ATestNGClassWithBrokenSetupMethod.name }, _)
        then: 1 * processor.completed(3, { it.resultType == ResultType.SKIPPED})

        then: 1 * processor.completed(1, { it.resultType == null})
        0 * processor._
    }

    void "executes test class with dependency method"() {
        when: process(ATestNGClassWithBrokenDependencyMethod)

        then: 1 * processor.started({ it.id == 1} , _)
        then: 1 * processor.started({ it.name == 'beforeMethod' && it.className == ATestNGClassWithBrokenDependencyMethod.name }, _)

        then: 1 * processor.failure(2, ATestNGClassWithBrokenDependencyMethod.failure)
        then: 1 * processor.completed(2, { it.resultType == ResultType.FAILURE })

        then: 1 * processor.started({ it.name == 'test' && it.className == ATestNGClassWithBrokenDependencyMethod.name }, _)
        then: 1 * processor.completed(3, { it.resultType == ResultType.SKIPPED})

        then: 1 * processor.completed(1, { it.resultType == null})
        0 * processor._
    }

    void "includes and excludes groups"() {
        given:
        _ * options.getIncludeGroups() >> ['group1', 'group2']
        _ * options.getExcludeGroups() >> ['group3']

        when: process(ATestNGClassWithGroups)

        then:
        1 * processor.started({ it.id == 1} , _)
        1 * processor.started({ it.name == 'group1' && it.className == ATestNGClassWithGroups.name }, _)
        1 * processor.started({ it.name == 'group2' && it.className == ATestNGClassWithGroups.name }, _)
        3 * processor.completed(_, _)
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
}

public class ATestNGClass {
    @BeforeClass
    public void beforeClass() {
    }

    @AfterClass
    public void afterClass() {
    }

    @BeforeMethod
    public void beforeMethod() {
    }

    @AfterMethod
    public void afterMethod() {
    }

    @org.testng.annotations.Test
    public void ok() {
    }

    @org.testng.annotations.Test(enabled = false)
    public void skipped() {
    }
}

public class ATestNGClassWithExpectedException {
    @org.testng.annotations.Test(expectedExceptions = RuntimeException)
    public void ok() {
        throw new RuntimeException()
    }
}

public class ATestNGClassWithManyMethods {
    @org.testng.annotations.Test public void ok() {}
    @org.testng.annotations.Test public void ok2() {}
    @org.testng.annotations.Test public void another() {}
    @org.testng.annotations.Test public void yetAnother() {}
}

public class ATestNGClassWithGroups {
    @org.testng.annotations.Test(groups="group1")
    public void group1() {
    }

    @org.testng.annotations.Test(groups="group2")
    public void group2() {
    }

    @org.testng.annotations.Test(groups="group2,group3")
    public void excluded() {
    }

    @org.testng.annotations.Test(groups="group4")
    public void ignored() {
    }
}

public class ATestNGFactoryClass {
    @Factory
    public Object[] suite() {
        return [new ATestNGClass()] as Object[]
    }
}

public class ATestNGClassWithBrokenConstructor {
    static RuntimeException failure = new RuntimeException()

    def ATestNGClassWithBrokenConstructor() {
        throw failure
    }

    @org.testng.annotations.Test
    public void test() {
    }
}

public class ATestNGClassWithBrokenSetupMethod {
    static RuntimeException failure = new RuntimeException()

    @BeforeMethod
    public void beforeMethod() {
        throw failure
    }

    @org.testng.annotations.Test
    public void test() {
    }
}

public class ATestNGClassWithBrokenDependencyMethod {
    static RuntimeException failure = new RuntimeException()

    @org.testng.annotations.Test
    public void beforeMethod() {
        throw failure
    }

    @org.testng.annotations.Test(dependsOnMethods = 'beforeMethod')
    public void test() {
    }
}