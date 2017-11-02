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

import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.processors.TestMainAction;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.io.LineBufferingOutputStream;
import org.gradle.internal.io.TextStream;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.time.Clock;
import org.gradle.process.internal.DefaultExecHandleBuilder;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleBuilder;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;

public class XcTestExecuter implements TestExecuter<XCTestTestExecutionSpec> {
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
        File executable = testTestExecutionSpec.getRunScript();
        File workingDir = testTestExecutionSpec.getWorkingDir();
        TestClassProcessor processor = objectFactory.newInstance(XcTestProcessor.class, executable, workingDir, getExecHandleBuilder(), getIdGenerator());

        Runnable detector = new XcTestDetector(processor);

        Object testTaskOperationId = getBuildOperationExcecutor().getCurrentOperation().getParentId();

        new TestMainAction(detector, processor, testResultProcessor, getTimeProvider(), testTaskOperationId, testTestExecutionSpec.getPath(), "Gradle Test Run " + testTestExecutionSpec.getPath()).run();
    }

    private static class XcTestDetector implements Runnable {
        private final TestClassProcessor testClassProcessor;

        XcTestDetector(TestClassProcessor testClassProcessor) {
            this.testClassProcessor = testClassProcessor;
        }

        @Override
        public void run() {
            TestClassRunInfo testClass = new DefaultTestClassRunInfo("All");
            testClassProcessor.processTestClass(testClass);
        }
    }

    static class XcTestProcessor implements TestClassProcessor {
        private TestResultProcessor resultProcessor;
        private ExecHandle execHandle;
        private final ExecHandleBuilder execHandleBuilder;
        private final IdGenerator<?> idGenerator;
        private final Clock clock;

        @Inject
        public XcTestProcessor(Clock clock, File executable, File workingDir, ExecHandleBuilder execHandleBuilder, IdGenerator<?> idGenerator) {
            this.execHandleBuilder = execHandleBuilder;
            this.idGenerator = idGenerator;
            this.clock = clock;
            execHandleBuilder.executable(executable);
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
            Deque<XCTestDescriptor> testDescriptors = new ArrayDeque<XCTestDescriptor>();
            TextStream stdOut = new XcTestScraper(TestOutputEvent.Destination.StdOut, resultProcessor, idGenerator, clock, testDescriptors);
            TextStream stdErr = new XcTestScraper(TestOutputEvent.Destination.StdErr, resultProcessor, idGenerator, clock, testDescriptors);
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
}
