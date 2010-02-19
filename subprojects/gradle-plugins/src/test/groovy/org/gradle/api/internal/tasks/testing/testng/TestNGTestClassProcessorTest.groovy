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
import org.gradle.api.internal.tasks.testing.TestInternal
import org.gradle.api.internal.tasks.testing.TestResultProcessor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.TestResult.ResultType
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.api.testing.fabric.TestClassRunInfo
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

@RunWith(JMock.class)
class TestNGTestClassProcessorTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final TestResultProcessor resultProcessor = context.mock(TestResultProcessor.class);
    private final TestNGTestClassProcessor processor = new TestNGTestClassProcessor(tmpDir.dir, new TestNGOptions(tmpDir.dir), [], new LongIdGenerator());

    @Test
    public void executesATestClass() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal suite ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestNGClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.id, equalTo(2L))
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestNGClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(nullValue()))
            will { TestInternal suite ->
                assertThat(suite.id, equalTo(1L))
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClass.class));
        processor.endProcessing();
    }

    @Test
    public void executesAFactoryTestClass() {
        context.checking {
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal suite ->
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestNGClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestNGClass.class.name))
            }
            one(resultProcessor).completed(withParam(notNullValue()), withParam(nullValue()))
            will { TestInternal suite ->
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGFactoryClass.class));
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

            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal suite ->
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
            inSequence(sequence)

            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { TestInternal test, TestResult result ->
                assertThat(test.name, equalTo('beforeMethod'))
                assertThat(test.className, equalTo(ATestNGClassWithBrokenSetupMethod.class.name))
                assertThat(result.resultType, equalTo(ResultType.FAILURE))
                assertThat(result.exception, sameInstance(ATestNGClassWithBrokenSetupMethod.failure))
            }
            inSequence(sequence)

            one(resultProcessor).started(withParam(notNullValue()))
            will { TestInternal test ->
                assertThat(test.name, equalTo('test'))
                assertThat(test.className, equalTo(ATestNGClassWithBrokenSetupMethod.class.name))
            }
            inSequence(sequence)

            one(resultProcessor).completed(withParam(notNullValue()), withParam(notNullValue()))
            will { TestInternal test, TestResult result ->
                assertThat(test.name, equalTo('test'))
                assertThat(test.className, equalTo(ATestNGClassWithBrokenSetupMethod.class.name))
                assertThat(result.resultType, equalTo(ResultType.SKIPPED))
            }
            inSequence(sequence)

            one(resultProcessor).completed(withParam(notNullValue()), withParam(nullValue()))
            will { TestInternal suite ->
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
            inSequence(sequence)
        }

        processor.startProcessing(resultProcessor);
        processor.processTestClass(testClass(ATestNGClassWithBrokenSetupMethod.class));
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