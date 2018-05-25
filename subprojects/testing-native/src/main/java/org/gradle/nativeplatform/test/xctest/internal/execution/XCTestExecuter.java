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

import com.google.common.collect.Lists;
import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.processors.TestMainAction;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.io.LineBufferingOutputStream;
import org.gradle.internal.io.TextStream;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.time.Clock;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecException;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.process.internal.ExecHandleFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Takes an XCTestTestExecutionSpec and executes the given test binary.
 *
 * This class is mostly responsible for managing the starting/stopping of the test process and wiring together the
 * different test execution bits (output scraping, event generation, process handling).
 *
 * NOTE: Eventually, we would like to replace some of this with a lower level integration with XCTest, which would
 * get rid of the output scraping and allow us to do things like:
 *
 * - Parallel test execution
 * - Smarter/fancier test filtering
 * - Test probing (so we know which tests exist without executing them)
 */
public class XCTestExecuter implements TestExecuter<XCTestTestExecutionSpec> {
    @Inject
    public ExecHandleFactory getExecHandleFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public BuildOperationExecutor getBuildOperationExcecutor() {
        throw new UnsupportedOperationException();
    }

    public IdGenerator<?> getIdGenerator() {
        return new LongIdGenerator();
    }

    @Inject
    public Clock getClock() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public Clock getTimeProvider() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void execute(XCTestTestExecutionSpec testExecutionSpec, TestResultProcessor testResultProcessor) {
        File executable = testExecutionSpec.getRunScript();
        File workingDir = testExecutionSpec.getWorkingDir();

        String rootTestSuiteId = testExecutionSpec.getPath();

        TestClassProcessor processor = new XCTestProcessor(getClock(), executable, workingDir, getExecHandleFactory().newExec(), getIdGenerator(), rootTestSuiteId);

        Runnable detector = new XCTestDetector(processor, testExecutionSpec.getTestSelection());

        Object testTaskOperationId = getBuildOperationExcecutor().getCurrentOperation().getParentId();

        new TestMainAction(detector, processor, testResultProcessor, getTimeProvider(), testTaskOperationId, rootTestSuiteId, "Gradle Test Run " + testExecutionSpec.getPath()).run();
    }

    @Override
    public void stopNow() {
        throw new UnsupportedOperationException("XCTest does not support failing fast on first test failure.");
    }

    private static class XCTestDetector implements Runnable {
        private final TestClassProcessor testClassProcessor;
        private final XCTestSelection testSelection;

        XCTestDetector(TestClassProcessor testClassProcessor, XCTestSelection testSelection) {
            this.testClassProcessor = testClassProcessor;
            this.testSelection = testSelection;
        }

        @Override
        public void run() {
            for (String includedTests : testSelection.getIncludedTests()) {
                TestClassRunInfo testClass = new DefaultTestClassRunInfo(includedTests);
                testClassProcessor.processTestClass(testClass);
            }
        }
    }

    static class XCTestProcessor implements TestClassProcessor {
        private TestResultProcessor resultProcessor;
        private ExecHandle execHandle;
        private final ExecHandleBuilder execHandleBuilder;
        private final IdGenerator<?> idGenerator;
        private final Clock clock;
        private final String rootTestSuiteId;

        @Inject
        public XCTestProcessor(Clock clock, File executable, File workingDir, ExecHandleBuilder execHandleBuilder, IdGenerator<?> idGenerator, String rootTestSuiteId) {
            this.execHandleBuilder = execHandleBuilder;
            this.idGenerator = idGenerator;
            this.clock = clock;
            this.rootTestSuiteId = rootTestSuiteId;
            execHandleBuilder.executable(executable);
            execHandleBuilder.setWorkingDir(workingDir);
        }

        @Override
        public void startProcessing(TestResultProcessor resultProcessor) {
            this.resultProcessor = resultProcessor;
        }

        @Override
        public void processTestClass(TestClassRunInfo testClass) {
            Deque<XCTestDescriptor> testDescriptors = new ArrayDeque<XCTestDescriptor>();
            TextStream stdOut = new XCTestScraper(TestOutputEvent.Destination.StdOut, resultProcessor, idGenerator, clock, rootTestSuiteId, testDescriptors);
            TextStream stdErr = new XCTestScraper(TestOutputEvent.Destination.StdErr, resultProcessor, idGenerator, clock, rootTestSuiteId, testDescriptors);

            execHandle = executeTest(testClass.getTestClassName(), new LineBufferingOutputStream(stdOut), new LineBufferingOutputStream(stdErr));
            try {
                execHandle.start();
                ExecResult result = execHandle.waitForFinish();
                // Exit code 0 = success
                // Exit code 1 = failed test(s)
                // anything else is considered an execution failure
                if (result.getExitValue() != 0 && result.getExitValue() != 1) {
                    result.rethrowFailure().assertNormalExitValue();
                }
            } catch (ExecException e) {
                stdOut.endOfStream(e);
                stdErr.endOfStream(null);
            } finally {
                execHandle = null;
            }
        }

        private ExecHandle executeTest(String testName, OutputStream outputStream, OutputStream errorStream) {
            execHandleBuilder.setArgs(toTestArgs(testName));
            execHandleBuilder.setStandardOutput(outputStream);
            execHandleBuilder.setErrorOutput(errorStream);
            return execHandleBuilder.build();
        }

        private static List<String> toTestArgs(String testName) {
            List<String> args = Lists.newArrayList();
            if (!testName.equals(XCTestSelection.INCLUDE_ALL_TESTS)) {
                if (OperatingSystem.current().isMacOsX()) {
                    args.add("-XCTest");
                }
                args.add(testName);
            }
            return args;
        }

        @Override
        public void stop() {
            if (execHandle != null) {
                execHandle.abort();
                execHandle.waitForFinish();
            }
        }

        @Override
        public void stopNow() {
            throw new UnsupportedOperationException("XCTest does not support failing fast on first test failure.");
        }
    }
}
