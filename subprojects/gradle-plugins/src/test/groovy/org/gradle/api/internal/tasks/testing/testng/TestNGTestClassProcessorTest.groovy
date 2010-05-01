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
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestResultProcessor

import org.gradle.api.tasks.testing.TestResult.ResultType
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.api.internal.tasks.testing.TestClassRunInfo
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TemporaryFolder
import org.jmock.Sequence
import org.jmock.integration.junit4.JMock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Factory
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.util.LongIdGenerator
import org.junit.Ignore
import org.gradle.api.internal.tasks.testing.TestCompleteEvent

@RunWith(JMock.class)
class TestNGTestClassProcessorTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final TestResultProcessor resultProcessor = context.mock(TestResultProcessor.class);
    private final TestNGOptions options = new TestNGOptions(tmpDir.dir)
    private final TestNGTestClassProcessor processor = new TestNGTestClassProcessor(tmpDir.dir, options, [], new LongIdGenerator());

    @Test
    public void executesATestClass() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal suite ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()), withParam(notNullValue()))
            will { TestDescriptorInternal test ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestNGClass.class.name))
            }
            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, equalTo(ResultType.SUCCESS))
                assertThat(event.failure, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
                assertThat(event.failure, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClass.class));
        processor.endProcessing();
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
                assertThat(event.failure, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
                assertThat(event.failure, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGFactoryClass.class));
        processor.endProcessing();
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
                assertThat(event.failure, nullValue())
            }
            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
                assertThat(event.failure, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithExpectedException.class));
        processor.endProcessing();
    }

    @Test @Ignore
    public void executesATestClassWithBrokenConstructor() {
        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithBrokenConstructor.class));
        processor.endProcessing();
        fail()
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

            one(resultProcessor).completed(withParam(equalTo(2L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, equalTo(ResultType.FAILURE))
                assertThat(event.failure, sameInstance(ATestNGClassWithBrokenSetupMethod.failure))
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
                assertThat(event.failure, nullValue())
            }
            inSequence(sequence)

            one(resultProcessor).completed(withParam(equalTo(1L)), withParam(notNullValue()))
            will { id, TestCompleteEvent event ->
                assertThat(event.resultType, nullValue())
                assertThat(event.failure, nullValue())
            }
            inSequence(sequence)
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithBrokenSetupMethod.class));
        processor.endProcessing();
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
        processor.endProcessing();
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

    @org.testng.annotations.Test
    public void ok() {
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

public static class ATestNGFactoryClass {
    @Factory
    public Object[] suite() {
        return [new ATestNGClass()] as Object[]
    }
}

public static class ATestNGClassWithBrokenConstructor {
    static RuntimeException failure = new RuntimeException()

    def ATestNGClassWithBrokenConstructor() {
        throw failure
    }

    @org.testng.annotations.Test
    public void test() {
    }
}

public static class ATestNGClassWithBrokenSetupMethod {
    static RuntimeException failure = new RuntimeException()

    @BeforeMethod
    public void beforeMethod() {
        throw failure
    }

    @org.testng.annotations.Test
    public void test() {
        System.out.println("EXECUTE");
    }
}