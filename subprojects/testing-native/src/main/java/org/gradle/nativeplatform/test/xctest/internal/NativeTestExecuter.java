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

package org.gradle.nativeplatform.test.xctest.internal;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.processors.TestMainAction;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.io.LineBufferingOutputStream;
import org.gradle.internal.io.TextStream;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.time.Clock;
import org.gradle.process.internal.DefaultExecHandleBuilder;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.util.TextUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NativeTestExecuter implements TestExecuter<XCTestTestExecutionSpec> {
    public ExecHandleBuilder getExecHandleBuilder() {
        return new DefaultExecHandleBuilder();
    }

    @Inject
    public BuildOperationExecutor getBuildOperationExcecutor() {
        throw new UnsupportedOperationException();
    }

    public IdGenerator<?> getIdGenerator() {
        return new LongIdGenerator();
    }

    @Inject
    public ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public Clock getTimeProvider() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void execute(XCTestTestExecutionSpec testTestExecutionSpec, TestResultProcessor testResultProcessor) {
        ObjectFactory objectFactory = getObjectFactory();
        File executable = testTestExecutionSpec.getTestBundleDir();
        File workingDir = testTestExecutionSpec.getWorkingDir();
        TestClassProcessor processor = objectFactory.newInstance(NativeTestClassProcessor.class, executable, workingDir, getExecHandleBuilder(), getIdGenerator());

        Runnable detector = new NativeTestDetector(processor);

        Object testTaskOperationId = getBuildOperationExcecutor().getCurrentOperation().getParentId();

        new TestMainAction(detector, processor, testResultProcessor, getTimeProvider(), testTaskOperationId, testTestExecutionSpec.getPath(), "Gradle Test Run " + testTestExecutionSpec.getPath()).run();
    }

    static class NativeTestDetector implements Runnable {
        private final TestClassProcessor testClassProcessor;

        NativeTestDetector(TestClassProcessor testClassProcessor) {
            this.testClassProcessor = testClassProcessor;
        }

        @Override
        public void run() {
            TestClassRunInfo testClass = new DefaultTestClassRunInfo("All");
            testClassProcessor.processTestClass(testClass);
        }
    }

    protected static class NativeTestClassProcessor implements TestClassProcessor {
        private TestResultProcessor resultProcessor;
        private ExecHandle execHandle;
        private final ExecHandleBuilder execHandleBuilder;
        private final IdGenerator<?> idGenerator;
        private final Clock clock;
        private final File bundle;

        @Inject
        public NativeTestClassProcessor(Clock clock, MacOSXCTestLocator xcTestLocator, File executable, File workingDir, ExecHandleBuilder execHandleBuilder, IdGenerator<?> idGenerator) {
            this.execHandleBuilder = execHandleBuilder;
            this.idGenerator = idGenerator;
            this.clock = clock;
            this.bundle = executable;
            execHandleBuilder.executable(xcTestLocator.find());
            execHandleBuilder.setWorkingDir(workingDir);
        }

        @Override
        public void startProcessing(TestResultProcessor resultProcessor) {
            this.resultProcessor = resultProcessor;
        }

        @Override
        public void processTestClass(TestClassRunInfo testClass) {
            execHandle = executeTest(testClass.getTestClassName());
            execHandle.waitForFinish();
        }

        private ExecHandle executeTest(String testName) {
            execHandleBuilder.setArgs(Arrays.asList("-XCTest", testName, bundle));
            Deque<XCTestDescriptor> testDescriptors = new ArrayDeque<XCTestDescriptor>();
            TextStream stdOut = new TextStreamToProcessor(TestOutputEvent.Destination.StdOut, resultProcessor, idGenerator, clock, testDescriptors);
            TextStream stdErr = new TextStreamToProcessor(TestOutputEvent.Destination.StdErr, resultProcessor, idGenerator, clock, testDescriptors);
            execHandleBuilder.setStandardOutput(new LineBufferingOutputStream(stdOut));
            execHandleBuilder.setErrorOutput(new LineBufferingOutputStream(stdErr));
            ExecHandle handle = execHandleBuilder.build();
            return handle.start();
        }

        @Override
        public void stop() {
            if (execHandle != null) {
                execHandle.abort();
                execHandle.waitForFinish();
            }
        }
    }

    private static class XCTestDescriptor {
        private final TestDescriptorInternal descriptorInternal;
        private final List<String> messages = Lists.newArrayList();

        public XCTestDescriptor(TestDescriptorInternal descriptorInternal) {
            this.descriptorInternal = descriptorInternal;
        }

        public TestDescriptorInternal getDescriptorInternal() {
            return descriptorInternal;
        }

        public List<String> getMessages() {
            return messages;
        }
    }

    private static class TextStreamToProcessor implements TextStream {
        private static final Pattern TEST_SUITE_NAME_PATTERN = Pattern.compile("'(\\p{Alnum}+)'");
        private static final Pattern TEST_CASE_NAME_PATTERN = Pattern.compile("'-\\[\\p{Alnum}+.(\\p{Alnum}+) (\\p{Alnum}+)]'");
        private static final Pattern TEST_FAILURE_PATTERN = Pattern.compile(":\\d+: error: -\\[\\p{Alnum}+.(\\p{Alnum}+) (\\p{Alnum}+)] : (.*)");

        private final TestResultProcessor processor;
        private final TestOutputEvent.Destination destination;
        private final IdGenerator<?> idGenerator;
        private final Clock clock;
        private final Deque<XCTestDescriptor> testDescriptors;
        private TestDescriptorInternal lastDescriptor;

        private TextStreamToProcessor(TestOutputEvent.Destination destination, TestResultProcessor processor, IdGenerator<?> idGenerator, Clock clock, Deque<XCTestDescriptor> testDescriptors) {
            this.processor = processor;
            this.destination = destination;
            this.idGenerator = idGenerator;
            this.clock = clock;
            this.testDescriptors = testDescriptors;
        }

        @Override
        public void text(String text) {
            synchronized (testDescriptors) {
                if (text.startsWith("Test Suite")) {
                    Matcher testSuiteMatcher = TEST_SUITE_NAME_PATTERN.matcher(text);
                    if (!testSuiteMatcher.find()) {
                        return;
                    }
                    String testSuite = testSuiteMatcher.group(1);

                    if (text.contains("started at")) {
                        TestDescriptorInternal testDescriptor = new DefaultTestClassDescriptor(idGenerator.generateId(), testSuite);  // Using DefaultTestClassDescriptor to fake JUnit test

                        processor.started(testDescriptor, new TestStartEvent(clock.getCurrentTime()));
                        testDescriptors.push(new XCTestDescriptor(testDescriptor));
                    } else {
                        XCTestDescriptor xcTestDescriptor = testDescriptors.pop();
                        lastDescriptor = xcTestDescriptor.getDescriptorInternal();
                        TestDescriptorInternal testDescriptor = xcTestDescriptor.getDescriptorInternal();
                        TestResult.ResultType resultType = TestResult.ResultType.SUCCESS;
                        if (text.contains("failed at")) {
                            resultType = TestResult.ResultType.FAILURE;
                        }

                        processor.completed(testDescriptor.getId(), new TestCompleteEvent(clock.getCurrentTime(), resultType));
                    }
                } else if (text.startsWith("Test Case")) {
                    Matcher testCaseMatcher = TEST_CASE_NAME_PATTERN.matcher(text);
                    testCaseMatcher.find();
                    String testSuite = testCaseMatcher.group(1);
                    String testCase = testCaseMatcher.group(2);

                    if (text.contains("started.")) {
                        TestDescriptorInternal testDescriptor = new DefaultTestMethodDescriptor(idGenerator.generateId(), testSuite, testCase);

                        processor.started(testDescriptor, new TestStartEvent(clock.getCurrentTime()));
                        testDescriptors.push(new XCTestDescriptor(testDescriptor));
                    } else {
                        XCTestDescriptor xcTestDescriptor = testDescriptors.pop();
                        lastDescriptor = xcTestDescriptor.getDescriptorInternal();
                        TestDescriptorInternal testDescriptor = xcTestDescriptor.getDescriptorInternal();
                        TestResult.ResultType resultType = TestResult.ResultType.SUCCESS;
                        if (text.contains("failed (")) {
                            resultType = TestResult.ResultType.FAILURE;
                            processor.failure(testDescriptor.getId(), new Throwable(Joiner.on(TextUtil.getPlatformLineSeparator()).join(xcTestDescriptor.getMessages())));
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
                            String testSuite = failureMessageMatcher.group(1);
                            String testCase = failureMessageMatcher.group(2);
                            String message = failureMessageMatcher.group(3);

                            if (testDescriptor.getClassName().equals(testSuite) && testDescriptor.getName().equals(testCase)) {
                                xcTestDescriptor.getMessages().add(message);
                            }
                        }

                    // If no current test can be associated to the output, the last known descriptor is used.
                    // See https://bugs.swift.org/browse/SR-1127 for more information.
                    } else if (lastDescriptor != null) {
                        processor.output(lastDescriptor.getId(), new DefaultTestOutputEvent(destination, text));
                    }
                }
            }
        }

        @Override
        public void endOfStream(@Nullable Throwable failure) {
            if (failure != null) {
                while (!testDescriptors.isEmpty()) {
                    processor.failure(testDescriptors.pop(), failure);
                }
            }
        }
    }
}
