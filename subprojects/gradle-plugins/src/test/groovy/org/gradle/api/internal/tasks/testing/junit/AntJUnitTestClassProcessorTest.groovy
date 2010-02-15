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
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestSuite
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

@RunWith(JMock.class)
class AntJUnitTestClassProcessorTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final TestListener listener = context.mock(TestListener.class);
    private final AntJUnitTestClassProcessor processor = new AntJUnitTestClassProcessor(tmpDir.dir);

    @Test
    public void executesATestClass() {
        context.checking {
            one(listener).beforeSuite(withParam(notNullValue()))
            will { TestSuite suite ->
                assertThat(suite.name, equalTo(ATestClass.class.name))
                assertThat(suite.className, equalTo(ATestClass.class.name))
            }
            one(listener).beforeTest(withParam(notNullValue()))
            will { org.gradle.api.tasks.testing.Test test ->
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClass.class.name))
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            will { org.gradle.api.tasks.testing.Test test ->
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClass.class.name))
            }
            one(listener).afterSuite(withParam(notNullValue()))
            will { TestSuite suite ->
                assertThat(suite.name, equalTo(ATestClass.class.name))
                assertThat(suite.className, equalTo(ATestClass.class.name))
            }
        }

        processor.startProcessing(listener);
        processor.processTestClass(testClass(ATestClass.class));
        processor.endProcessing();
    }

    @Test
    public void executesAJUnit3TestClass() {
        context.checking {
            one(listener).beforeSuite(withParam(notNullValue()))
            will { TestSuite suite ->
                assertThat(suite.name, equalTo(AJunit3TestClass.class.name))
                assertThat(suite.className, equalTo(AJunit3TestClass.class.name))
            }
            one(listener).beforeTest(withParam(notNullValue()))
            will { org.gradle.api.tasks.testing.Test test ->
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            will { org.gradle.api.tasks.testing.Test test ->
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(listener).afterSuite(withParam(notNullValue()))
            will { TestSuite suite ->
                assertThat(suite.name, equalTo(AJunit3TestClass.class.name))
                assertThat(suite.className, equalTo(AJunit3TestClass.class.name))
            }
        }

        processor.startProcessing(listener);
        processor.processTestClass(testClass(AJunit3TestClass.class));
        processor.endProcessing();
    }

    @Test
    public void executesATestClassWithRunWithAnnotation() {
        context.checking {
            one(listener).beforeSuite(withParam(notNullValue()))
            will { TestSuite suite ->
                assertThat(suite.name, equalTo(ATestClassWithRunner.class.name))
                assertThat(suite.className, equalTo(ATestClassWithRunner.class.name))
            }
            one(listener).beforeTest(withParam(notNullValue()))
            will { org.gradle.api.tasks.testing.Test test ->
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClassWithRunner.class.name))
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            will { org.gradle.api.tasks.testing.Test test ->
                assertThat(test.name, equalTo('ok'))
                assertThat(test.className, equalTo(ATestClassWithRunner.class.name))
            }
            one(listener).afterSuite(withParam(notNullValue()))
            will { TestSuite suite ->
                assertThat(suite.name, equalTo(ATestClassWithRunner.class.name))
                assertThat(suite.className, equalTo(ATestClassWithRunner.class.name))
            }
        }

        processor.startProcessing(listener);
        processor.processTestClass(testClass(ATestClassWithRunner.class));
        processor.endProcessing();
    }

    @Test
    public void executesATestClassWithASuiteMethod() {
        context.checking {
            one(listener).beforeSuite(withParam(notNullValue()))
            will { TestSuite suite ->
                assertThat(suite.name, equalTo(ATestClassWithSuiteMethod.class.name))
                assertThat(suite.className, equalTo(ATestClassWithSuiteMethod.class.name))
            }
            one(listener).beforeTest(withParam(notNullValue()))
            will { org.gradle.api.tasks.testing.Test test ->
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(listener).afterTest(withParam(notNullValue()), withParam(notNullValue()))
            will { org.gradle.api.tasks.testing.Test test ->
                assertThat(test.name, equalTo('testOk'))
                assertThat(test.className, equalTo(AJunit3TestClass.class.name))
            }
            one(listener).afterSuite(withParam(notNullValue()))
            will { TestSuite suite ->
                assertThat(suite.name, equalTo(ATestClassWithSuiteMethod.class.name))
                assertThat(suite.className, equalTo(ATestClassWithSuiteMethod.class.name))
            }
        }

        processor.startProcessing(listener);
        processor.processTestClass(testClass(ATestClassWithSuiteMethod.class));
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
        return new junit.framework.TestSuite(AJunit3TestClass.class);
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
        description.addChild(Description.createTestDescription(type, 'ok'))
        return description
    }

    @Override
    public void run(RunNotifier runNotifier) {
        Description description = Description.createTestDescription(type, 'ok')
        runNotifier.fireTestStarted(description)
        runNotifier.fireTestFinished(description)
    }
}
