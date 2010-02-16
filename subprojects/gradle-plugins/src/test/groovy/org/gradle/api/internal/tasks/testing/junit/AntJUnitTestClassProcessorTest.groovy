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




package org.gradle.api.internal.tasks.testing.junit

import junit.framework.TestCase
import org.gradle.api.internal.tasks.testing.TestInternal
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.testing.fabric.TestClassRunInfo
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TemporaryFolder
import org.jmock.integration.junit4.JMock
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.junit.runner.notification.Failure
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.TestResult.ResultType

@RunWith(JMock.class)
class AntJUnitTestClassProcessorTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final TestResultProcessor resultProcessor = context.mock(TestResultProcessor.class);
    private final AntJUnitTestClassProcessor processor = new AntJUnitTestClassProcessor(tmpDir.dir);

    @Test
    public void executesATestClass() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal suite ->
                assertThat(suite.id, equalTo(0L))
                assertThat(suite.name, equalTo(ATestClass.class.name))
                assertThat(suite.className, equalTo(ATestClass.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(1L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(1L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(nullValue()))
            will { TestInternal suite ->
                assertThat(suite.id, equalTo(0L))
                assertThat(suite.name, equalTo(ATestClass.class.name))
                assertThat(suite.className, equalTo(ATestClass.class.name))
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClass.class));
        processor.endProcessing();
    }

    @Test
    public void executesAJUnit3TestClass() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal suite ->
                assertThat(suite.name, equalTo(AJunit3TestClass.class.name))
                assertThat(suite.className, equalTo(AJunit3TestClass.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(nullValue()))
            will { TestInternal suite ->
                assertThat(suite.name, equalTo(AJunit3TestClass.class.name))
                assertThat(suite.className, equalTo(AJunit3TestClass.class.name))
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(AJunit3TestClass.class));
        processor.endProcessing();
    }

    @Test
    public void executesMultipleTestClasses() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal suite ->
                assertThat(suite.id, equalTo(0L))
                assertThat(suite.name, equalTo(ATestClass.class.name))
                assertThat(suite.className, equalTo(ATestClass.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(1L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(1L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(nullValue()))
            will { TestInternal suite ->
                assertThat(suite.id, equalTo(0L))
                assertThat(suite.name, equalTo(ATestClass.class.name))
                assertThat(suite.className, equalTo(ATestClass.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal suite ->
                assertThat(suite.id, equalTo(2L))
                assertThat(suite.name, equalTo(AJunit3TestClass.class.name))
                assertThat(suite.className, equalTo(AJunit3TestClass.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(3L))
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(3L))
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(nullValue()))
            will { TestInternal suite ->
                assertThat(suite.id, equalTo(2L))
                assertThat(suite.name, equalTo(AJunit3TestClass.class.name))
                assertThat(suite.className, equalTo(AJunit3TestClass.class.name))
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClass.class));
        processor.processTestClass(testClass(AJunit3TestClass.class));
        processor.endProcessing();
    }

    @Test
    public void executesATestClassWithRunWithAnnotation() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal suite ->
                assertThat(suite.id, equalTo(0L))
                assertThat(suite.name, equalTo(ATestClassWithRunner.class.name))
                assertThat(suite.className, equalTo(ATestClassWithRunner.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(1L))
                assertThat(test.name, equalTo('broken'))
                assertThat(test.className, equalTo(ATestClassWithRunner.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClassWithRunner.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { TestInternal test, TestResult result ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClassWithRunner.class.name))
                assertThat(result.resultType, equalTo(ResultType.SUCCESS))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { TestInternal test, TestResult result ->
                assertThat(test.id, equalTo(1L))
                assertThat(test.name, equalTo('broken'))
                assertThat(test.className, equalTo(ATestClassWithRunner.class.name))
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, notNullValue())
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(nullValue()))
            will { TestInternal suite ->
                assertThat(suite.id, equalTo(0L))
                assertThat(suite.name, equalTo(ATestClassWithRunner.class.name))
                assertThat(suite.className, equalTo(ATestClassWithRunner.class.name))
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithRunner.class));
        processor.endProcessing();
    }

    @Test
    public void executesATestClassWithASuiteMethod() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal suite ->
                assertThat(suite.name, equalTo(ATestClassWithSuiteMethod.class.name))
                assertThat(suite.className, equalTo(ATestClassWithSuiteMethod.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(1L))
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(1L))
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(nullValue()))
            will { TestInternal suite ->
                assertThat(suite.name, equalTo(ATestClassWithSuiteMethod.class.name))
                assertThat(suite.className, equalTo(ATestClassWithSuiteMethod.class.name))
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithSuiteMethod.class));
        processor.endProcessing();
    }

    private TestClassRunInfo testClass(Class<?> testClass) {
        TestClassRunInfo runInfo = context.mock(TestClassRunInfo.class, testClass.name)
        context.checking {
            allowing(runInfo).getTestClassName()
            will(returnValue(testClass.name))
        }
        return runInfo;
    }
}

public static class ATestClass {
    @Test
    public void ok() {
    }

    @Test @Ignore
    public void ignored() {
    }
}

public static class AJunit3TestClass extends TestCase {
    public void testOk() {
    }
}

public static class ATestClassWithSuiteMethod {
    public static junit.framework.Test suite() {
        return new junit.framework.TestSuite(AJunit3TestClass.class, AJunit3TestClass.class);
    }
}

@RunWith(CustomRunner.class)
public static class ATestClassWithRunner {}

public static class CustomRunner extends Runner {
    Class<?> type

    def CustomRunner(Class<?> type) {
        this.type = type
    }

    @Override
    public Description getDescription() {
        Description description = Description.createSuiteDescription(type)
        description.addChild(Description.createTestDescription(type, 'ok1'))
        description.addChild(Description.createTestDescription(type, 'ok2'))
        return description
    }

    @Override
    public void run(RunNotifier runNotifier) {
        // Run tests in 'parallel'
        Description test1 = Description.createTestDescription(type, 'broken')
        Description test2 = Description.createTestDescription(type, 'ok')
        runNotifier.fireTestStarted(test1)
        runNotifier.fireTestStarted(test2)
        runNotifier.fireTestFailure(new Failure(test1, new RuntimeException('broken')))
        runNotifier.fireTestFinished(test2)
        runNotifier.fireTestFinished(test1)
    }
}
