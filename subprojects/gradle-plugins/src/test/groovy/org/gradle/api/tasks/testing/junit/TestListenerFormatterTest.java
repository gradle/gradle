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
            one(testListenerMock).suiteStarting(with(aSuiteWithName(TEST_SUITE_NAME)));
        }});

        testListenerFormatter.startTestSuite(jUnitTestMock);
    }

    @Test
    public void testEndTestSuite() {
        context.checking(new Expectations() {{
            one(testListenerMock).suiteFinished(with(aSuiteWithName(TEST_SUITE_NAME)));
        }});

        testListenerFormatter.endTestSuite(jUnitTestMock);
    }

    @Test
    public void testStartTest() {
        context.checking(new Expectations() {{
            one(testListenerMock).testStarting(with(aTestWithName(TEST_NAME)));
        }});

        testListenerFormatter.startTest(testMock);
    }

    @Test
    public void testEndTestSuccess() {
        context.checking(new Expectations() {{
            one(testListenerMock).testFinished(with(aTestWithName(TEST_NAME)), with(aResultOf(TestListener.ResultType.SUCCESS, null)));
        }});

        testListenerFormatter.endTest(testMock);
    }

    @Test
    public void testEndTestWithError() {
        context.checking(new Expectations() {{
            one(testListenerMock).testFinished(with(aTestWithName(TEST_NAME)), with(aResultOf(TestListener.ResultType.FAILURE, error)));
        }});

        testListenerFormatter.addError(testMock, error);
        testListenerFormatter.endTest(testMock);
    }

    @Test
    public void testEndTestWithFailure() {
        context.checking(new Expectations() {{
            one(testListenerMock).testFinished(with(aTestWithName(TEST_NAME)), with(aResultOf(TestListener.ResultType.FAILURE, failure)));
        }});

        testListenerFormatter.addFailure(testMock, failure);
        testListenerFormatter.endTest(testMock);
    }

    @Factory
    private static Matcher<TestListener.Suite> aSuiteWithName(String name) {
        return new SuiteHasNameMatcher(name);
    }

    @Factory
    private static Matcher<TestListener.Test> aTestWithName(String value) {
        return new TestHasNameMatcher(value);
    }

    @Factory
    private static Matcher<TestListener.Result> aResultOf(TestListener.ResultType type, Throwable throwable) {
        return new TestResultMatcher(type, throwable);
    }

    private static class SuiteHasNameMatcher extends TypeSafeMatcher<TestListener.Suite> {
        private String name;

        public SuiteHasNameMatcher(String name) {
            this.name = name;
        }

        public boolean matchesSafely(TestListener.Suite suite) {
            return suite.getName().equals(name);
        }

        public void describeTo(Description description) {
            description.appendText("a suite with name ").appendValue(name);            
        }
    }

    private static class TestHasNameMatcher extends TypeSafeMatcher<TestListener.Test> {
        private String name;

        public TestHasNameMatcher(String name) {
            this.name = name;
        }

        public boolean matchesSafely(TestListener.Test test) {
            return test.getName().equals(name);
        }

        public void describeTo(Description description) {
            description.appendText("a test with name ").appendValue(name);
        }
    }

    private static class TestResultMatcher extends TypeSafeMatcher<TestListener.Result> {
        private TestListener.ResultType type;
        private Throwable throwable;

        public TestResultMatcher(TestListener.ResultType type, Throwable throwable) {
            this.type = type;
            this.throwable = throwable;
        }

        public boolean matchesSafely(TestListener.Result result) {
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

