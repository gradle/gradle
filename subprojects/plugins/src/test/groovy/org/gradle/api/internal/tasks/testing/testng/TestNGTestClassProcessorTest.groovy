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
import org.gradle.api.internal.tasks.testing.TestClassRunInfo
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.tasks.testing.TestResult.ResultType
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.internal.id.LongIdGenerator
import org.gradle.logging.StandardOutputRedirector
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.testng.annotations.*
import spock.lang.Ignore
import spock.lang.Specification

class TestNGTestClassProcessorTest extends Specification {

    @Rule TestNameTestDirectoryProvider reportDir = new TestNameTestDirectoryProvider()

    private resultProcessor = Mock(TestResultProcessor)

    private TestNGSpec options
    private TestNGTestClassProcessor processor

    void setup(){
        options = Spy(TestNGSpec, constructorArgs:[new TestNGOptions(reportDir.testDirectory)]);
        processor = new TestNGTestClassProcessor(reportDir.testDirectory, options, [], new LongIdGenerator(), {} as StandardOutputRedirector);
    }

    void "executes the test class"() {
        when:
        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClass.class));
        processor.stop();

        then:
        1 * resultProcessor.started({ it.id == 1 && it.name == 'Gradle test' && it.className == null }, { it.parentId == null })
        then:
        1 * resultProcessor.started({ it.id == 2 && it.name == 'ok' && it.className == ATestNGClass.class.name }, { it.parentId == 1 })

        then:
        1 * resultProcessor.completed(2, { it.resultType == ResultType.SUCCESS })
        then:
        1 * resultProcessor.completed(1, { it.resultType == null })

        0 * resultProcessor._
    }

    void "executes factory test class"() {
        when:
        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGFactoryClass.class));
        processor.stop();

        then:
        1 * resultProcessor.started({ it.name == 'Gradle test' && it.className == null }, { it.parentId == null })
        1 * resultProcessor.started({ it.name == 'ok' && it.className == ATestNGClass.class.name }, _ as TestStartEvent)

        1 * resultProcessor.completed(2, { it.resultType == ResultType.SUCCESS })
        1 * resultProcessor.completed(1, { it.resultType == null })

        0 * resultProcessor._
    }

    void "executes test with expected exception"() {
        when:
        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithExpectedException.class));
        processor.stop();

        then:
        1 * resultProcessor.started({ it.id == 1} , _)
        1 * resultProcessor.started({ it.name == 'ok' && it.className == ATestNGClassWithExpectedException.class.name }, _)

        1 * resultProcessor.completed(2, { it.resultType == ResultType.SUCCESS })
        1 * resultProcessor.completed(1, { it.resultType == null })

        0 * resultProcessor._
    }

    void "executes test with broken setup"() {
        when:
        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithBrokenSetupMethod.class));
        processor.stop();

        then:
        1 * resultProcessor.started({ it.id == 1} , _)
        then:
        1 * resultProcessor.started({ it.name == 'beforeMethod' && it.className == ATestNGClassWithBrokenSetupMethod.class.name }, _)

        then:
        1 * resultProcessor.failure(2, ATestNGClassWithBrokenSetupMethod.failure)
        then:
        1 * resultProcessor.completed(2, { it.resultType == ResultType.FAILURE })

        then:
        1 * resultProcessor.started({ it.name == 'test' && it.className == ATestNGClassWithBrokenSetupMethod.class.name }, _)
        then:
        1 * resultProcessor.completed(3, { it.resultType == ResultType.SKIPPED})

        then:
        1 * resultProcessor.completed(1, { it.resultType == null})
        0 * resultProcessor._
    }

    void "executes test class with dependency method"() {
        when:
        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithBrokenDependencyMethod.class));
        processor.stop();

        then:
        1 * resultProcessor.started({ it.id == 1} , _)
        then:
        1 * resultProcessor.started({ it.name == 'beforeMethod' && it.className == ATestNGClassWithBrokenDependencyMethod.class.name }, _)

        then:
        1 * resultProcessor.failure(2, ATestNGClassWithBrokenDependencyMethod.failure)
        then:
        1 * resultProcessor.completed(2, { it.resultType == ResultType.FAILURE })

        then:
        1 * resultProcessor.started({ it.name == 'test' && it.className == ATestNGClassWithBrokenDependencyMethod.class.name }, _)
        then:
        1 * resultProcessor.completed(3, { it.resultType == ResultType.SKIPPED})

        then:
        1 * resultProcessor.completed(1, { it.resultType == null})
        0 * resultProcessor._
    }

    void "includes and excludes groups"() {
        given:
        _ * options.getIncludeGroups() >> ['group1', 'group2']
        _ * options.getExcludeGroups() >> ['group3']

        when:
        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithGroups.class));
        processor.stop();

        then:
        1 * resultProcessor.started({ it.id == 1} , _)
        1 * resultProcessor.started({ it.name == 'group1' && it.className == ATestNGClassWithGroups.class.name }, _)
        1 * resultProcessor.started({ it.name == 'group2' && it.className == ATestNGClassWithGroups.class.name }, _)
        3 * resultProcessor.completed(_, _)
        0 * resultProcessor._
    }

    @Ignore //not implemented yet
    void "executes class with broken constructor"() {
        when:
        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithBrokenConstructor.class));
        processor.stop();

        then:
        //below needs to revisited when we attempt to fix the problem
        //e.g. decide what's the behavior we want in this scenario
        1 * resultProcessor.started({ it.id == 1} , _)
        1 * resultProcessor.started({ it.name == 'initializationError' && it.className == ATestNGClassWithBrokenConstructor.class.name }, _)
        1 * resultProcessor.failure(1, ATestNGClassWithBrokenConstructor.failure)
        1 * resultProcessor.completed(1, { it.resultType == ResultType.FAILURE})
        0 * resultProcessor._
    }

    void "fails early for unknown test class"() {
        processor.startProcessing(resultProcessor)

        when:
        processor.processTestClass(testClass('unknown'))

        then:
        def ex = thrown(GradleException)
        ex.message == "Could not load test class \'unknown\'."
    }

    private TestClassRunInfo testClass(Class<?> type) {
        return testClass(type.name)
    }

    private TestClassRunInfo testClass(String testClassName) {
        return { testClassName } as TestClassRunInfo
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
    @org.testng.annotations.Test(expectedExceptions = RuntimeException.class)
    public void ok() {
        throw new RuntimeException()
    }
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