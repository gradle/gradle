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
import org.gradle.api.tasks.testing.TestResult.ResultType
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.internal.id.LongIdGenerator
import org.gradle.logging.StandardOutputRedirector
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TemporaryFolder
import org.jmock.Sequence
import org.jmock.integration.junit4.JMock
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.internal.tasks.testing.*
import org.testng.annotations.*

import static org.hamcrest.Matchers.*
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail

@RunWith(JMock.class)
class TestNGTestClassProcessorTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    @Rule public final TemporaryFolder reportDir = new TemporaryFolder();
    @Rule public final TemporaryFolder resultsDir = new TemporaryFolder();
    private final TestResultProcessor resultProcessor = context.mock(TestResultProcessor.class);
    private final TestNGOptions options = new TestNGOptions(reportDir.dir)
    private final TestNGTestClassProcessor processor = new TestNGTestClassProcessor(reportDir.dir, options, [], new LongIdGenerator(), {} as StandardOutputRedirector, resultsDir.dir, true);

    @Test
    public void executesATestClass() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite, TestStartEvent event ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
                assertThat(event.parentId, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test, TestStartEvent event ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestNGClass.class.name))
                assertThat(event.parentId, equalTo(1L))
            }
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, equalTo(ResultType.SUCCESS))
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClass.class));
        processor.stop();
    }

    @Test
    public void executesAFactoryTestClass() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestNGClass.class.name))
            }
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, equalTo(ResultType.SUCCESS))
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGFactoryClass.class));
        processor.stop();
    }

    @Test
    public void executesATestWithExpectedException() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestNGClassWithExpectedException.class.name))
            }
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, equalTo(ResultType.SUCCESS))
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithExpectedException.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithBrokenSetup() {
        context.checking {
            Sequence sequence = context.sequence('seq')

            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
            inSequence(sequence)

            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('beforeMethod'))
                assertThat(test.className, equalTo(ATestNGClassWithBrokenSetupMethod.class.name))
            }
            inSequence(sequence)

            one(resultProcessor).failure(2L, ATestNGClassWithBrokenSetupMethod.failure)

            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, equalTo(ResultType.FAILURE))
            }
            inSequence(sequence)

            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('test'))
                assertThat(test.className, equalTo(ATestNGClassWithBrokenSetupMethod.class.name))
            }
            inSequence(sequence)

            one(resultProcessor).completed(withParam(equalTo(3L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, equalTo(ResultType.SKIPPED))
            }
            inSequence(sequence)

            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            inSequence(sequence)
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithBrokenSetupMethod.class));
        processor.stop();
    }

    @Test
    public void executesATestClassWithDependencyMethod() {
        context.checking {
            Sequence sequence = context.sequence('seq')

            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
            inSequence(sequence)

            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('beforeMethod'))
                assertThat(test.className, equalTo(ATestNGClassWithBrokenDependencyMethod.class.name))
            }
            inSequence(sequence)

            one(resultProcessor).failure(2L, ATestNGClassWithBrokenDependencyMethod.failure)

            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, equalTo(ResultType.FAILURE))
            }
            inSequence(sequence)

            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('test'))
                assertThat(test.className, equalTo(ATestNGClassWithBrokenDependencyMethod.class.name))
            }
            inSequence(sequence)

            one(resultProcessor).completed(withParam(equalTo(3L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, equalTo(ResultType.SKIPPED))
            }
            inSequence(sequence)

            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
            }
            inSequence(sequence)
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithBrokenDependencyMethod.class));
        processor.stop();
    }

    @Test
    public void canIncludeAndExcludeGroups() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('group1'))
                assertThat(test.className, equalTo(ATestNGClassWithGroups.class.name))
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('group2'))
                assertThat(test.className, equalTo(ATestNGClassWithGroups.class.name))
            }
            ignoring(resultProcessor).completed(withParam(anything()), withParam(anything()))
        }

        options.includeGroups('group1', 'group2')
        options.excludeGroups('group3')
        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithGroups.class));
        processor.stop();
    }

    @Test @Ignore
    public void executesATestClassWithBrokenConstructor() {
        context.checking {
            Sequence sequence = context.sequence('seq')

            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.name, equalTo('initializationError'))
                assertThat(test.className, equalTo(ATestNGClassWithBrokenConstructor.class.name))
            }
            inSequence(sequence)

            one(resultProcessor).failure(1L, ATestNGClassWithBrokenConstructor.failure)

            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, equalTo(ResultType.FAILURE))
            }
            inSequence(sequence)
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithBrokenConstructor.class));
        processor.stop();
    }

    @Test
    public void failsEarlyForUnknownTestClass() {
        processor.startProcessing(resultProcessor)
        try {
            processor.processTestClass(testClass('unknown'))
            fail()
        } catch (GradleException e) {
            assertThat(e.message, equalTo('Could not load test class \'unknown\'.'))
        }
    }

    private TestClassRunInfo testClass(Class<?> type) {
        return testClass(type.name)
    }

    private TestClassRunInfo testClass(String testClassName) {
        TestClassRunInfo runInfo = context.mock(TestClassRunInfo.class)
        context.checking {
            allowing(runInfo).getTestClassName()
            will(returnValue(testClassName))
        }
        return runInfo;
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