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

package org.gradle.nativeplatform.test.xctest.internal.execution;

import com.google.common.base.Joiner;
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.io.TextStream;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.time.Clock;
import org.gradle.util.internal.TextUtil;

import javax.annotation.Nullable;
import java.util.Deque;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes XCTest's output and converts them into {@code TestResultProcessor} events.
 *
 * NOTE: We eventually want to get rid of this and use our own hooks in the test process itself.
 */
class XCTestScraper implements TextStream {
    private static final Pattern TEST_FAILURE_PATTERN = Pattern.compile(":\\d+: error: (-\\[\\p{Alnum}+.)?(\\p{Alnum}+)[ .](\\p{Alnum}+)]? : (.*)");

    private final TestResultProcessor processor;
    private final TestOutputEvent.Destination destination;
    private final IdGenerator<?> idGenerator;
    private final Clock clock;
    private final String rootTestSuiteId;
    private final Deque<XCTestDescriptor> testDescriptors;
    private TestDescriptorInternal lastDescriptor;
    private StringBuilder textBuilder = new StringBuilder();

    XCTestScraper(TestOutputEvent.Destination destination, TestResultProcessor processor, IdGenerator<?> idGenerator, Clock clock, String rootTestSuiteId, Deque<XCTestDescriptor> testDescriptors) {
        this.processor = processor;
        this.destination = destination;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.rootTestSuiteId = rootTestSuiteId;
        this.testDescriptors = testDescriptors;
    }

    @Override
    public void text(String textFragment) {
        textBuilder.append(textFragment);
        if (!textFragment.endsWith(SystemProperties.getInstance().getLineSeparator())) {
            return;
        }
        String text = textBuilder.toString();
        textBuilder = new StringBuilder();
        synchronized (testDescriptors) {
            Scanner scanner = new Scanner(text).useDelimiter("'");
            if (scanner.hasNext()) {
                String token = scanner.next().trim();
                if (token.equals("Test Suite")) {
                    // Test Suite 'PassingTestSuite' started at 2017-10-30 10:45:47.828
                    String testSuite = scanner.next();
                    if (testSuite.equals("All tests") || testSuite.equals("Selected tests") || testSuite.endsWith(".xctest")) {
                        // ignore these test suites
                        return;
                    }
                    String status = scanner.next();
                    boolean started = status.contains("started at");

                    if (started) {
                        TestDescriptorInternal testDescriptor = new DefaultTestClassDescriptor(idGenerator.generateId(), testSuite);  // Using DefaultTestClassDescriptor to fake JUnit test
                        processor.started(testDescriptor, new TestStartEvent(clock.getCurrentTime()));
                        testDescriptors.push(new XCTestDescriptor(testDescriptor));
                    } else {
                        XCTestDescriptor xcTestDescriptor = testDescriptors.pop();
                        lastDescriptor = xcTestDescriptor.getDescriptorInternal();
                        TestDescriptorInternal testDescriptor = xcTestDescriptor.getDescriptorInternal();
                        TestResult.ResultType resultType = TestResult.ResultType.SUCCESS;
                        boolean failed = status.contains("failed at");
                        if (failed) {
                            resultType = TestResult.ResultType.FAILURE;
                        }

                        processor.completed(testDescriptor.getId(), new TestCompleteEvent(clock.getCurrentTime(), resultType));
                    }
                } else if (token.equals("Test Case")) {
                    // (macOS) Looks like: Test Case '-[AppTest.PassingTestSuite testCanPassTestCaseWithAssertion]' started.
                    // (Linux) Looks like: Test Case 'PassingTestSuite.testCanPassTestCaseWithAssertion' started.
                    String testSuiteAndCase = scanner.next();
                    String[] splits = testSuiteAndCase.
                        replace('[', ' ').
                        replace(']', ' ').
                        split("[. ]");
                    String testSuite;
                    String testCase;
                    if (OperatingSystem.current().isMacOsX()) {
                        testSuite = splits[2];
                        testCase = splits[3];
                    } else {
                        testSuite = splits[0];
                        testCase = splits[1];
                    }

                    String status = scanner.next().trim();
                    boolean started = status.contains("started");

                    if (started) {
                        TestDescriptorInternal testDescriptor = new DefaultTestMethodDescriptor(idGenerator.generateId(), testSuite, testCase);
                        processor.started(testDescriptor, new TestStartEvent(clock.getCurrentTime()));
                        testDescriptors.push(new XCTestDescriptor(testDescriptor));
                    } else {
                        XCTestDescriptor xcTestDescriptor = testDescriptors.pop();
                        lastDescriptor = xcTestDescriptor.getDescriptorInternal();
                        TestDescriptorInternal testDescriptor = xcTestDescriptor.getDescriptorInternal();
                        TestResult.ResultType resultType = TestResult.ResultType.SUCCESS;
                        boolean failed = status.contains("failed");
                        if (failed) {
                            resultType = TestResult.ResultType.FAILURE;
                            Throwable failure = new Throwable(Joiner.on(TextUtil.getPlatformLineSeparator()).join(xcTestDescriptor.getMessages()));
                            processor.failure(testDescriptor.getId(), TestFailure.fromTestFrameworkFailure(failure));
                        }

                        processor.completed(testDescriptor.getId(), new TestCompleteEvent(clock.getCurrentTime(), resultType));
                    }
                } else {
                    XCTestDescriptor xcTestDescriptor = testDescriptors.peek();
                    if (xcTestDescriptor != null) {
                        TestDescriptorInternal testDescriptor = xcTestDescriptor.getDescriptorInternal();

                        processor.output(testDescriptor.getId(), new DefaultTestOutputEvent(destination, text));

                        Matcher failureMessageMatcher = TEST_FAILURE_PATTERN.matcher(text);
                        if (failureMessageMatcher.find()) {
                            String testSuite = failureMessageMatcher.group(2);
                            String testCase = failureMessageMatcher.group(3);
                            String message = failureMessageMatcher.group(4);

                            if (testDescriptor.getClassName().equals(testSuite) && testDescriptor.getName().equals(testCase)) {
                                xcTestDescriptor.getMessages().add(message);
                            }
                        }

                        // If no current test can be associated to the output, the last known descriptor is used.
                        // See https://bugs.swift.org/browse/SR-1127 for more information.
                    } else if (lastDescriptor != null) {
                        processor.output(lastDescriptor.getId(), new DefaultTestOutputEvent(destination, text));
                    } else {
                        // If there is no known last descriptor, associate it with the root test suite
                        processor.output(rootTestSuiteId, new DefaultTestOutputEvent(destination, text));
                    }
                }
            }
        }
    }

    @Override
    public void endOfStream(@Nullable Throwable failure) {
        if (failure != null) {
            synchronized (testDescriptors) {
                Object testId;
                if (!testDescriptors.isEmpty()) {
                    testId = testDescriptors.pop().getDescriptorInternal().getId();
                } else {
                    testId = rootTestSuiteId;
                }
                processor.failure(testId, TestFailure.fromTestFrameworkFailure(failure));
                testDescriptors.clear();
            }
        }
    }
}
