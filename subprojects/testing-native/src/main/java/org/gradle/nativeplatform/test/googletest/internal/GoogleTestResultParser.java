/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.nativeplatform.test.googletest.internal;

import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.io.TextStream;
import org.gradle.internal.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;





/**
 * Parses the 'raw output mode' results of native tests using GTest that run from shell, and informs
 * a ITestRunListener of the results.
 * <p>Sample format of output expected:
 *
 * <pre>
 * [==========] Running 15 tests from 1 test case.
 * [----------] Global test environment set-up.
 * [----------] 15 tests from MessageTest
 * [ RUN      ] MessageTest.DefaultConstructor
 * [       OK ] MessageTest.DefaultConstructor (1 ms)
 * [ RUN      ] MessageTest.CopyConstructor
 * external/gtest/test/gtest-message_test.cc:67: Failure
 * Value of: 5
 * Expected: 2
 * external/gtest/test/gtest-message_test.cc:68: Failure
 * Value of: 1 == 1
 * Actual: true
 * Expected: false
 * [  FAILED  ] MessageTest.CopyConstructor (2 ms)
 *  ...
 * [ RUN      ] MessageTest.DoesNotTakeUpMuchStackSpace
 * [       OK ] MessageTest.DoesNotTakeUpMuchStackSpace (0 ms)
 * [----------] 15 tests from MessageTest (26 ms total)
 *
 * [----------] Global test environment tear-down
 * [==========] 15 tests from 1 test case ran. (26 ms total)
 * [  PASSED  ] 6 tests.
 * [  FAILED  ] 9 tests, listed below:
 * [  FAILED  ] MessageTest.CopyConstructor
 * [  FAILED  ] MessageTest.ConstructsFromCString
 * [  FAILED  ] MessageTest.StreamsCString
 * [  FAILED  ] MessageTest.StreamsNullCString
 * [  FAILED  ] MessageTest.StreamsString
 * [  FAILED  ] MessageTest.StreamsStringWithEmbeddedNUL
 * [  FAILED  ] MessageTest.StreamsNULChar
 * [  FAILED  ] MessageTest.StreamsInt
 * [  FAILED  ] MessageTest.StreamsBasicIoManip
 * 9 FAILED TESTS
 * </pre>
 *
 * <p>where the following tags are used to signal certain events:
 * <pre>
 * [==========]: the first occurrence indicates a new run started, including the number of tests
 *                  to be expected in this run
 * [ RUN      ]: indicates a new test has started to run; a series of zero or more lines may
 *                  follow a test start, and will be captured in case of a test failure or error
 * [       OK ]: the preceding test has completed successfully, optionally including the time it
 *                  took to run (in ms)
 * [  FAILED  ]: the preceding test has failed, optionally including the time it took to run (in ms)
 * [==========]: the preceding test run has completed, optionally including the time it took to run
 *                  (in ms)
 * </pre>
 *
 * All other lines are ignored.
 *
 * Originally from: https://android.googlesource.com/platform/tools/tradefederation/+/6d5a7589392fd101fd58db8ed7b324790f3e7b48/src/com/android/tradefed/testtype/GTestResultParser.java
 */
public class GoogleTestResultParser implements TextStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleTestResultParser.class);

    private static final Pattern START_OF_TEST_SUITE_PATTERN = Pattern.compile("\\[----------\\] \\d+ test[s]? from (.*)");
    private static final Pattern END_OF_TEST_SUITE_PATTERN = Pattern.compile("\\[----------\\] \\d+ test[s]? from (.*) \\(\\d+ ms total\\)");

    private final Deque<TestDescriptorInternal> testDescriptors = new ArrayDeque<TestDescriptorInternal>();

    private final TestResultProcessor listener;
    private final String testRunName;
    private final IdGenerator<?> idGenerator;
    private final Clock clock;
    private boolean testsCompleted;
    private boolean failed;

    private static class ParsedTestInfo {
        private final String testClassName;
        private final String testName;

        ParsedTestInfo(String testClassName, String testName) {
            this.testClassName = testClassName;
            this.testName = testName;
        }
    }

    /**
     * Prefixes used to demarcate and identify output.
     */
    private static class Prefixes {
        private static final String INFORMATIONAL_MARKER = "[----------]";
        private static final String TEST_RUN_MARKER = "[==========]";
        private static final String START_TEST_MARKER = "[ RUN      ]";
        private static final String OK_TEST_MARKER = "[       OK ]";
        private static final String FAILED_TEST_MARKER = "[  FAILED  ]";
    }

    /**
     * Creates the GoogleTestResultParser for a single listener.
     *
     * @param runName the test run name to provide to
     * @param listener informed of test results as the tests are executing
     */
    public GoogleTestResultParser(String runName, TestResultProcessor listener, IdGenerator<?> idGenerator, Clock clock) {
        this.testRunName = runName;
        this.listener = listener;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     */
    public void text(String line) {
        parse(line);
    }

    @Override
    public void endOfStream(@Nullable Throwable failure) {
        // TODO:
    }

    /**
     * Parse an individual output line.
     *
     * @param line Text output line
     */
    private void parse(String line) {
        // TODO: Capture global setup and teardown.
        if (!testsCompleted) {
            if (line.startsWith(Prefixes.START_TEST_MARKER)) {
                // Individual test started
                String message = line.substring(Prefixes.START_TEST_MARKER.length()).trim();
                processTestStartedTag(message);
            } else if (line.startsWith(Prefixes.OK_TEST_MARKER)) {
                // Individual test completed successfully
                String message = line.substring(Prefixes.OK_TEST_MARKER.length()).trim();
                processOKTag(message);
            } else if (line.startsWith(Prefixes.FAILED_TEST_MARKER)) {
                // Individual test completed with failure
                String message = line.substring(Prefixes.FAILED_TEST_MARKER.length()).trim();
                processFailedTag(message);
            } else if (line.startsWith(Prefixes.INFORMATIONAL_MARKER)) {
                String trimmedLine = line.trim();
                Matcher endOfTestSuite = END_OF_TEST_SUITE_PATTERN.matcher(trimmedLine);
                if (endOfTestSuite.matches()) {
                    // Test run ended
                    // This is for the end of the test suite run, so make sure this else-if is after the
                    // check for START_TEST_SUITE_MARKER
                    processRunCompletedTag(endOfTestSuite.group(1));
                } else {
                    // eg: 15 tests from MessageTest
                    Matcher startOfTestSuite = START_OF_TEST_SUITE_PATTERN.matcher(trimmedLine);
                    if (startOfTestSuite.matches()) {
                        processRunStartedTag(startOfTestSuite.group(1));
                    }
                }
            } else if (line.startsWith(Prefixes.TEST_RUN_MARKER)) {
                if (!line.contains("Running")) {
                    testsCompleted = true;
                }
            } else if (!testDescriptors.isEmpty()) {
                // Note this does not handle the case of an error outside an actual test run
                appendTestOutputLine(line);
            }
        }
    }

    /**
     * Parses and stores the test identifier (class and test name).
     *
     */
    private void processRunStartedTag(String testClassName) {
        // Using DefaultTestClassDescriptor to fake JUnit test
        TestDescriptorInternal testDescriptor = new DefaultTestClassDescriptor(idGenerator.generateId(), testClassName);
        listener.started(testDescriptor, new TestStartEvent(clock.getCurrentTime()));
        testDescriptors.push(testDescriptor);
    }

    /**
     * Processes and informs listener when we encounter a tag indicating that a test suite is done.
     *
     */
    private void processRunCompletedTag(String testClassName) {
        TestDescriptorInternal testDescriptor = testDescriptors.pop();
        listener.completed(testDescriptor.getId(), new TestCompleteEvent(clock.getCurrentTime(), failed ? TestResult.ResultType.FAILURE : TestResult.ResultType.SUCCESS));
    }

    /**
     * Parse the test identifier (class and test name), and optional time info.
     *
     * @param identifier Raw identifier of the form classname.testname, with an optional time element in the format of (XX ms) at the end
     * @return A ParsedTestInfo representing the parsed info from the identifier string.
     *
     * If no time tag was detected, then the third element in the array (time_in_ms) will be null. If the line failed to parse properly (eg: could not determine name of test/class) then an "UNKNOWN"
     * string value will be returned for the classname and testname. This method guarantees a string will always be returned for the class and test names (but not for the time value).
     */
    private ParsedTestInfo parseTestIdentifier(String identifier) {
        String[] testId = identifier.split("\\.");
        if (testId.length < 2) {
            LOGGER.error("Could not detect the test class and test name, received: " + identifier);
            return null;
        } else {
            return new ParsedTestInfo(testId[0], testId[1]);
        }
    }

    /**
     * Processes and informs listener when we encounter a tag indicating that a test has started.
     *
     * @param identifier Raw log output of the form classname.testname, with an optional time (x ms)
     */
    private void processTestStartedTag(String identifier) {
        ParsedTestInfo parsedResults = parseTestIdentifier(identifier);
        // Using DefaultTestClassDescriptor to fake JUnit test
        TestDescriptorInternal testDescriptor = new DefaultTestMethodDescriptor(idGenerator.generateId(), parsedResults.testClassName, parsedResults.testName);
        listener.started(testDescriptor, new TestStartEvent(clock.getCurrentTime()));
        testDescriptors.push(testDescriptor);
    }

    /**
     * Helper method to do the work necessary when a test has ended.
     *
     * @param identifier
     * @param testPassed Indicates whether the test passed or failed (set to true if passed, false if failed)
     */
    private void doTestEnded(String identifier, boolean testPassed) {
        if (testDescriptors.isEmpty()) {
            throw new IllegalStateException("No test/class name is currently recorded as running! " + identifier);
        }
        TestDescriptorInternal testDescriptor = testDescriptors.pop();

        if (testPassed) {
            listener.completed(testDescriptor.getId(), new TestCompleteEvent(clock.getCurrentTime(), TestResult.ResultType.SUCCESS));
        } else {
            listener.failure(testDescriptor.getId(), new Throwable());
            listener.completed(testDescriptor.getId(), new TestCompleteEvent(clock.getCurrentTime(), TestResult.ResultType.FAILURE));
        }
        // TODO:
        // failure while executing test?
        // listener.failure(currentTest, new Throwable(parsedResults + " failed"));
    }

    /**
     * Processes and informs listener when we encounter the OK tag.
     *
     * @param identifier Raw log output of the form "classname.testname" with an optional (XX ms) at the end indicating the running time.
     */
    private void processOKTag(String identifier) {
        doTestEnded(identifier, true);
    }

    /**
     * Processes and informs listener when we encounter the FAILED tag.
     *
     * @param identifier Raw log output of the form "classname.testname" with an optional (XX ms) at the end indicating the running time.
     */
    private void processFailedTag(String identifier) {
        doTestEnded(identifier, false);
        failed = true;
    }

    /**
     * Appends the test output to the current TestResult.
     *
     * @param line Raw test result line of output.
     */
    private void appendTestOutputLine(String line) {
        listener.output(testDescriptors.peek().getId(), new DefaultTestOutputEvent(TestOutputEvent.Destination.StdErr, line));
    }
}
