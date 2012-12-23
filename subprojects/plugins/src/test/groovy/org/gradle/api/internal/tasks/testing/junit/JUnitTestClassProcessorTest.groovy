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
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.internal.tasks.testing.TestClassRunInfo
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.internal.id.LongIdGenerator
import org.gradle.util.TemporaryFolder
import org.jmock.integration.junit4.JMock
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runner.Runner
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.logging.StandardOutputRedirector
import org.junit.BeforeClass
import junit.extensions.TestSetup
import org.junit.After
import org.gradle.api.tasks.testing.TestResult
import junit.framework.TestSuite
import org.gradle.messaging.actor.ActorFactory
import org.gradle.messaging.actor.TestActorFactory

@RunWith(JMock.class)
class JUnitTestClassProcessorTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final TestResultProcessor resultProcessor = context.mock(TestResultProcessor.class);
    private final ActorFactory actorFactory = new TestActorFactory()
    private final JUnitTestClassProcessor processor = new JUnitTestClassProcessor(tmpDir.dir, new LongIdGenerator(), actorFactory, {} as StandardOutputRedirector);

    @Test
    public void executesAJUnit4TestClass() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite, TestStartEvent event ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo(ATestClass.class.name))
                assertThat(suite.className, equalTo(ATestClass.class.name))
                assertThat(event.parentId, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test, TestStartEvent event ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClass.class.name))
                assertThat(event.parentId, equalTo(1L))
            }
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClass.class));
        processor.stop();
    }

    @Test
    public void executesABrokenJUnit4TestClass() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite, TestStartEvent event ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo(ABrokenTestClass.class.name))
                assertThat(suite.className, equalTo(ABrokenTestClass.class.name))
                assertThat(event.parentId, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test, TestStartEvent event ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('broken'))
                assertThat(test.className, equalTo(ABrokenTestClass.class.name))
                assertThat(event.parentId, equalTo(1L))
            }
            one(resultProcessor).failure(2L, ABrokenTestClass.failure)
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ABrokenTestClass.class));
        processor.stop();
    }

    @Test
    public void executesAJUnit4TestClassWithIgnoredTest() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite, TestStartEvent event ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo(ATestClassWithIgnoredMethod.class.name))
                assertThat(suite.className, equalTo(ATestClassWithIgnoredMethod.class.name))
                assertThat(event.parentId, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test, TestStartEvent event ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('ignored'))
                assertThat(test.className, equalTo(ATestClassWithIgnoredMethod.class.name))
                assertThat(event.parentId, equalTo(1L))
            }
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, equalTo(TestResult.ResultType.SKIPPED))
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithIgnoredMethod.class));
        processor.stop();
    }

    @Test
    public void executesAnIgnoredJUnit4TestClass() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite, TestStartEvent event ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo(AnIgnoredTestClass.class.name))
                assertThat(suite.className, equalTo(AnIgnoredTestClass.class.name))
                assertThat(event.parentId, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test, TestStartEvent event ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('ignored'))
                assertThat(test.className, equalTo(AnIgnoredTestClass.class.name))
                assertThat(event.parentId, equalTo(1L))
            }
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, equalTo(TestResult.ResultType.SKIPPED))
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(AnIgnoredTestClass.class));
        processor.stop();
    }

    @Test
    public void executesAJUnit3TestClass() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite, TestStartEvent event ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo(AJunit3TestClass.class.name))
                assertThat(suite.className, equalTo(AJunit3TestClass.class.name))
                assertThat(event.parentId, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test, TestStartEvent event ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
                assertThat(event.parentId, equalTo(1L))
            }
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(AJunit3TestClass.class));
        processor.stop();
    }

    @Test
    public void executesABrokenJUnit3TestClass() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite, TestStartEvent event ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo(ABrokenJunit3TestClass.class.name))
                assertThat(suite.className, equalTo(ABrokenJunit3TestClass.class.name))
                assertThat(event.parentId, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test, TestStartEvent event ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('testBroken'))
                assertThat(test.className, equalTo(ABrokenJunit3TestClass.class.name))
                assertThat(event.parentId, equalTo(1L))
            }
            one(resultProcessor).failure(2L, ABrokenJunit3TestClass.failure)
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ABrokenJunit3TestClass.class));
        processor.stop();
    }

    @Test
    public void executesMultipleTestClasses() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite, TestStartEvent event ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo(ATestClass.class.name))
                assertThat(suite.className, equalTo(ATestClass.class.name))
                assertThat(event.parentId, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test, TestStartEvent event ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClass.class.name))
                assertThat(event.parentId, equalTo(1L))
            }
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite, TestStartEvent event ->
                assertThat(suite.id, equalTo(3L))
                assertThat(suite.name, equalTo(AJunit3TestClass.class.name))
                assertThat(suite.className, equalTo(AJunit3TestClass.class.name))
                assertThat(event.parentId, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test, TestStartEvent event ->
                assertThat(test.id, equalTo(4L))
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
                assertThat(event.parentId, equalTo(3L))
            }
            one(resultProcessor).completed(withParam(equalTo(4L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(3L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClass.class));
        processor.processTestClass(testClass(AJunit3TestClass.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithRunWithAnnotation() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo(ATestClassWithRunner.class.name))
                assertThat(suite.className, equalTo(ATestClassWithRunner.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('broken'))
                assertThat(test.className, equalTo(ATestClassWithRunner.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.id, equalTo(3L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClassWithRunner.class.name))
            }
            one(resultProcessor).completed(withParam(equalTo(3L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).failure(2L, CustomRunner.failure)
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithRunner.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithASuiteMethod() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo(ATestClassWithSuiteMethod.class.name))
                assertThat(suite.className, equalTo(ATestClassWithSuiteMethod.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.id, equalTo(3L))
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(resultProcessor).completed(withParam(equalTo(3L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithSuiteMethod.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithBrokenSuiteMethod() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo(ATestClassWithBrokenSuiteMethod.class.name))
                assertThat(suite.className, equalTo(ATestClassWithBrokenSuiteMethod.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('initializationError'))
                assertThat(test.className, equalTo(ATestClassWithBrokenSuiteMethod.class.name))
            }
            one(resultProcessor).failure(2L, ATestClassWithBrokenSuiteMethod.failure)
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithBrokenSuiteMethod.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithBrokenSetUp() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo(ATestSetUpWithBrokenSetUp.class.name))
                assertThat(suite.className, equalTo(ATestSetUpWithBrokenSetUp.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo(AJunit3TestClass.name))
                assertThat(test.className, equalTo(ATestSetUpWithBrokenSetUp.class.name))
            }
            one(resultProcessor).failure(2L, ATestSetUpWithBrokenSetUp.failure)
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestSetUpWithBrokenSetUp.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithBrokenBeforeMethod() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo(ATestClassWithBrokenBeforeMethod.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('test'))
                assertThat(test.className, equalTo(ATestClassWithBrokenBeforeMethod.class.name))
            }
            one(resultProcessor).failure(2L, ATestClassWithBrokenBeforeMethod.failure)
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithBrokenBeforeMethod.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithBrokenBeforeAndAfterMethod() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo(ATestClassWithBrokenBeforeAndAfterMethod.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('test'))
                assertThat(test.className, equalTo(ATestClassWithBrokenBeforeAndAfterMethod.class.name))
            }
            one(resultProcessor).failure(2L, ATestClassWithBrokenBeforeAndAfterMethod.beforeFailure)
            one(resultProcessor).failure(2L, ATestClassWithBrokenBeforeAndAfterMethod.afterFailure)
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithBrokenBeforeAndAfterMethod.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithBrokenConstructor() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo(ATestClassWithBrokenConstructor.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('test'))
                assertThat(test.className, equalTo(ATestClassWithBrokenConstructor.class.name))
            }
            one(resultProcessor).failure(2L, ATestClassWithBrokenConstructor.failure)
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithBrokenConstructor.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithBrokenBeforeClassMethod() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo(ATestClassWithBrokenBeforeClassMethod.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('classMethod'))
                assertThat(test.className, equalTo(ATestClassWithBrokenBeforeClassMethod.class.name))
            }
            one(resultProcessor).failure(2L, ATestClassWithBrokenBeforeClassMethod.failure)
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithBrokenBeforeClassMethod.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithRunnerThatCannotBeConstructed() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo(ATestClassWithUnconstructableRunner.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('initializationError'))
                assertThat(test.className, equalTo(ATestClassWithUnconstructableRunner.class.name))
            }
            one(resultProcessor).failure(2L, CustomRunnerWithBrokenConstructor.failure)
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithUnconstructableRunner.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithRunnerThatBreaksBeforeRunningAnyTests() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo(ATestClassWithBrokenRunner.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('initializationError'))
                assertThat(test.className, equalTo(ATestClassWithBrokenRunner.class.name))
            }
            one(resultProcessor).failure(2L, CustomRunnerWithBrokenRunMethod.failure)
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithBrokenRunner.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithRunnerThatBreaksAfterRunningSomeTests() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo(ATestClassWithRunnerThatBreaksAfterRuningSomeTests.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('ok1'))
                assertThat(test.className, equalTo(ATestClassWithRunnerThatBreaksAfterRuningSomeTests.class.name))
            }
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('broken'))
                assertThat(test.className, equalTo(ATestClassWithRunnerThatBreaksAfterRuningSomeTests.class.name))
            }
            one(resultProcessor).failure(3L, CustomRunnerWithRunMethodThatBreaksAfterRunningSomeTests.failure)
            one(resultProcessor).completed(withParam(equalTo(3L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestClassWithRunnerThatBreaksAfterRuningSomeTests.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWhichCannotBeLoaded() {
        String testClassName = 'org.gradle.api.internal.tasks.testing.junit.ATestClassWhichCannotBeLoaded'

        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo(testClassName))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('initializationError'))
                assertThat(test.className, equalTo(testClassName))
            }
            one(resultProcessor).failure(withParam(equalTo(2L)), withParam(instanceOf(NoClassDefFoundError)))
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(testClassName));
        processor.stop();
    }

    @Test
    public void executesAJUnit3TestClassThatRenamesItself() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite, TestStartEvent event ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo(AJunit3TestThatRenamesItself.class.name))
                assertThat(suite.className, equalTo(AJunit3TestThatRenamesItself.class.name))
                assertThat(event.parentId, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test, TestStartEvent event ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestThatRenamesItself.class.name))
                assertThat(event.parentId, equalTo(1L))
            }
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(AJunit3TestThatRenamesItself.class));
        processor.stop();
    }

    private TestClassRunInfo testClass(Class<?> type) {
        return testClass(type.name)
    }

    private TestClassRunInfo testClass(String testClassName) {
        TestClassRunInfo runInfo = context.mock(TestClassRunInfo.class, testClassName)
        context.checking {
            allowing(runInfo).getTestClassName()
            will(returnValue(testClassName))
        }
        return runInfo;
    }
}

public class ATestClass {
    @Test
    public void ok() {
    }
}

public class ATestClassWithIgnoredMethod {
    @Test @Ignore
    public void ignored() {
    }
}

@Ignore
public class AnIgnoredTestClass {
    @Test
    public void ignored() {
    }
}

public class ABrokenTestClass {
    static RuntimeException failure = new RuntimeException()

    @Test
    public void broken() {
        throw failure.fillInStackTrace()
    }
}

public class ATestClassWithBrokenConstructor {
    static RuntimeException failure = new RuntimeException()

    def ATestClassWithBrokenConstructor() {
        throw failure.fillInStackTrace()
    }

    @Test
    public void test() {
    }
}

public class ATestClassWithBrokenBeforeMethod {
    static RuntimeException failure = new RuntimeException()

    @Before
    public void setup() {
        throw failure.fillInStackTrace()
    }

    @Test
    public void test() {
    }
}

public class ATestClassWithBrokenBeforeAndAfterMethod {
    static RuntimeException beforeFailure = new RuntimeException()
    static RuntimeException afterFailure = new RuntimeException()

    @Before
    public void setup() {
        throw beforeFailure.fillInStackTrace()
    }

    @After
    public void teardown() {
        throw afterFailure.fillInStackTrace()
    }

    @Test
    public void test() {
    }
}

public class ATestClassWithBrokenBeforeClassMethod {
    static RuntimeException failure = new RuntimeException()

    @BeforeClass
    public static void setup() {
        throw failure.fillInStackTrace()
    }

    @Test
    public void test() {
    }
}

public class AJunit3TestClass extends TestCase {
    public void testOk() {
    }
}

public class AJunit3TestThatRenamesItself extends TestCase {
    public void testOk() {
        setName('another test')
    }
}

public class ABrokenJunit3TestClass extends TestCase {
    static RuntimeException failure = new RuntimeException()

    public void testBroken() {
        throw failure.fillInStackTrace()
    }
}

public class ATestClassWithSuiteMethod {
    public static junit.framework.Test suite() {
        return new junit.framework.TestSuite(AJunit3TestClass.class, AJunit3TestClass.class);
    }
}

public class ATestClassWithBrokenSuiteMethod {
    static RuntimeException failure = new RuntimeException('broken')

    public static junit.framework.Test suite() {
        throw failure
    }
}

public class ATestSetUpWithBrokenSetUp extends TestSetup {
    static RuntimeException failure = new RuntimeException('broken')

    def ATestSetUpWithBrokenSetUp() {
        super(new TestSuite(AJunit3TestClass.class))
    }

    protected void setUp() {
        throw failure
    }

    public static junit.framework.Test suite() {
        return new ATestSetUpWithBrokenSetUp()
    }
}

@RunWith(CustomRunner.class)
public class ATestClassWithRunner {}

public class CustomRunner extends Runner {
    static RuntimeException failure = new RuntimeException('broken')
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
        runNotifier.fireTestFailure(new Failure(test1, failure.fillInStackTrace()))
        runNotifier.fireTestFinished(test2)
        runNotifier.fireTestFinished(test1)
    }
}

@RunWith(CustomRunnerWithBrokenConstructor.class)
public class ATestClassWithUnconstructableRunner {}

public class CustomRunnerWithBrokenConstructor extends Runner {
    static RuntimeException failure = new RuntimeException()

    def CustomRunnerWithBrokenConstructor(Class<?> type) {
        throw failure.fillInStackTrace()
    }

    Description getDescription() {
        throw new UnsupportedOperationException();
    }

    void run(RunNotifier notifier) {
        throw new UnsupportedOperationException();
    }
}

@RunWith(CustomRunnerWithBrokenRunMethod.class)
public class ATestClassWithBrokenRunner {}

public class CustomRunnerWithBrokenRunMethod extends Runner {
    static RuntimeException failure = new RuntimeException()
    final Class<?> type

    def CustomRunnerWithBrokenRunMethod(Class<?> type) {
        this.type = type
    }

    Description getDescription() {
        return Description.createSuiteDescription(type)
    }

    void run(RunNotifier notifier) {
        throw failure.fillInStackTrace();
    }
}

@RunWith(CustomRunnerWithRunMethodThatBreaksAfterRunningSomeTests.class)
public class ATestClassWithRunnerThatBreaksAfterRuningSomeTests {}

public class CustomRunnerWithRunMethodThatBreaksAfterRunningSomeTests extends Runner {
    static RuntimeException failure = new RuntimeException()
    final Class<?> type

    def CustomRunnerWithRunMethodThatBreaksAfterRunningSomeTests(Class<?> type) {
        this.type = type
    }

    Description getDescription() {
        return Description.createSuiteDescription(type)
    }

    void run(RunNotifier notifier) {
        notifier.fireTestStarted(Description.createTestDescription(type, "ok1"))
        notifier.fireTestFinished(Description.createTestDescription(type, "ok1"))
        notifier.fireTestStarted(Description.createTestDescription(type, "broken"))
        throw failure.fillInStackTrace();
    }
}

public class ATestClassWhichCannotBeLoaded {
    static {
        throw new NoClassDefFoundError()
    }
}
