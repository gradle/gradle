/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.testing.junit;

import org.gradle.api.tasks.testing.TestSuite;
import org.junit.Test;
import org.junit.Before;
import org.junit.internal.matchers.TypeSafeMatcher;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.api.tasks.testing.TestListener;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;
import org.jmock.Expectations;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import junit.framework.TestResult;
import junit.framework.AssertionFailedError;

public class TestListenerFormatterTest {
    private static final String TEST_SUITE_NAME = "TestSuiteName";
    private static final String TEST_NAME = "A Test Name";

    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();

    private TestListener testListenerMock;
    private JUnitTest jUnitTestMock;
    private junit.framework.Test testMock;
    private Throwable error = new Throwable();
    private AssertionFailedError failure = new AssertionFailedError();

    private TestListenerFormatter testListenerFormatter;

    @Before
    public void setUp() throws Exception {
        testListenerMock = context.mock(TestListener.class);
        jUnitTestMock = new JUnitTest(TEST_SUITE_NAME);
        testMock = new junit.framework.Test() {
            public int countTestCases() { return 0; }
            public void run(TestResult testResult) { }

            @Override
            public String toString() {
                return TEST_NAME;
            }
        };

        testListenerFormatter = new TestListenerFormatter(testListenerMock);
    }

    @Test
    public void testStartTestSuite() {
        context.checking(new Expectations() {{
            one(testListenerMock).beforeSuite(with(aSuiteWithName(TEST_SUITE_NAME)));
        }});

        testListenerFormatter.startTestSuite(jUnitTestMock);
    }

    @Test
    public void testEndTestSuite() {
        context.checking(new Expectations() {{
            one(testListenerMock).afterSuite(with(aSuiteWithName(TEST_SUITE_NAME)));
        }});

        testListenerFormatter.endTestSuite(jUnitTestMock);
    }

    @Test
    public void testStartTest() {
        context.checking(new Expectations() {{
            one(testListenerMock).beforeTest(with(aTestWithName(TEST_NAME)));
        }});

        testListenerFormatter.startTest(testMock);
    }

    @Test
    public void testEndTestSuccess() {
        context.checking(new Expectations() {{
            one(testListenerMock).afterTest(with(aTestWithName(TEST_NAME)), with(aResultOf(org.gradle.api.tasks.testing.TestResult.ResultType.SUCCESS, null)));
        }});

        testListenerFormatter.endTest(testMock);
    }

    @Test
    public void testEndTestWithError() {
        context.checking(new Expectations() {{
            one(testListenerMock).afterTest(with(aTestWithName(TEST_NAME)), with(aResultOf(org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE, error)));
        }});

        testListenerFormatter.addError(testMock, error);
        testListenerFormatter.endTest(testMock);
    }

    @Test
    public void testEndTestWithFailure() {
        context.checking(new Expectations() {{
            one(testListenerMock).afterTest(with(aTestWithName(TEST_NAME)), with(aResultOf(org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE, failure)));
        }});

        testListenerFormatter.addFailure(testMock, failure);
        testListenerFormatter.endTest(testMock);
    }

    @Factory
    private static Matcher<TestSuite> aSuiteWithName(String name) {
        return new SuiteHasNameMatcher(name);
    }

    @Factory
    private static Matcher<org.gradle.api.tasks.testing.Test> aTestWithName(String value) {
        return new TestHasNameMatcher(value);
    }

    @Factory
    private static Matcher<org.gradle.api.tasks.testing.TestResult> aResultOf(org.gradle.api.tasks.testing.TestResult.ResultType type, Throwable throwable) {
        return new TestResultMatcher(type, throwable);
    }

    private static class SuiteHasNameMatcher extends TypeSafeMatcher<TestSuite> {
        private String name;

        public SuiteHasNameMatcher(String name) {
            this.name = name;
        }

        public boolean matchesSafely(TestSuite suite) {
            return suite.getName().equals(name);
        }

        public void describeTo(Description description) {
            description.appendText("a suite with name ").appendValue(name);            
        }
    }

    private static class TestHasNameMatcher extends TypeSafeMatcher<org.gradle.api.tasks.testing.Test> {
        private String name;

        public TestHasNameMatcher(String name) {
            this.name = name;
        }

        public boolean matchesSafely(org.gradle.api.tasks.testing.Test test) {
            return test.getName().equals(name);
        }

        public void describeTo(Description description) {
            description.appendText("a test with name ").appendValue(name);
        }
    }

    private static class TestResultMatcher extends TypeSafeMatcher<org.gradle.api.tasks.testing.TestResult> {
        private org.gradle.api.tasks.testing.TestResult.ResultType type;
        private Throwable throwable;

        public TestResultMatcher(org.gradle.api.tasks.testing.TestResult.ResultType type, Throwable throwable) {
            this.type = type;
            this.throwable = throwable;
        }

        public boolean matchesSafely(org.gradle.api.tasks.testing.TestResult result) {
            switch (type)
            {
                case SUCCESS:
                    return result.getResultType() == type;
                case FAILURE:
                    return result.getResultType() == type && result.getException() == throwable;
                default:
                    return false;
            }
        }

        public void describeTo(Description description) {
            description.appendText("a result of ").appendValue(type);
        }
    }
}

