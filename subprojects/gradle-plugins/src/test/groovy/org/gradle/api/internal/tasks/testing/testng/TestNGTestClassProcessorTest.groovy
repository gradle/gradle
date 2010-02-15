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

import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestSuite
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.api.testing.fabric.TestClassRunInfo
import org.gradle.util.JUnit4GroovyMockery
import org.gradle.util.TemporaryFolder
import org.jmock.integration.junit4.JMock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.annotations.Factory
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.testng.annotations.BeforeClass
import org.testng.annotations.BeforeMethod
import org.testng.annotations.AfterClass

@RunWith(JMock.class)
class TestNGTestClassProcessorTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final TestListener listener = context.mock(TestListener.class);
    private final TestNGTestClassProcessor processor = new TestNGTestClassProcessor(tmpDir.dir, new TestNGOptions(tmpDir.dir), []);

    @Test
    public void executesATestClass() {
        context.checking {
            one(listener).beforeSuite(withParam(notNullValue()))
            will { TestSuite suite ->
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
            one(listener).beforeTest(withParam(notNullValue()))
            will { org.gradle.api.tasks.testing.Test test ->
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestNGClass.class.name))
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            will { org.gradle.api.tasks.testing.Test test ->
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestNGClass.class.name))
            }
            one(listener).afterSuite(withParam(notNullValue()))
            will { TestSuite suite ->
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
        }

        processor.startProcessing(listener);
        processor.processTestClass(testClass(ATestNGClass.class));
        processor.endProcessing();
    }

    @Test
    public void executesAFactoryTestClass() {
        context.checking {
            one(listener).beforeSuite(withParam(notNullValue()))
            will { TestSuite suite ->
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
            one(listener).beforeTest(withParam(notNullValue()))
            will { org.gradle.api.tasks.testing.Test test ->
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestNGClass.class.name))
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            will { org.gradle.api.tasks.testing.Test test ->
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestNGClass.class.name))
            }
            one(listener).afterSuite(withParam(notNullValue()))
            will { TestSuite suite ->
                assertThat(suite.name, equalTo('Gradle test'))
                assertThat(suite.className, nullValue())
            }
        }

        processor.startProcessing(listener);
        processor.processTestClass(testClass(ATestNGFactoryClass.class));
        processor.endProcessing();
    }

    private TestClassRunInfo testClass(Class<?> testClass) {
        TestClassRunInfo runInfo = context.mock(TestClassRunInfo.class)
        context.checking {
            allowing(runInfo).getTestClassName()
            will(returnValue(testClass.name))
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
